package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.dto.StockInRequest;
import com.grocery.model.InventoryBatch;
import com.grocery.model.InventoryTransaction;
import com.grocery.model.Notification;
import com.grocery.model.Product;
import com.grocery.model.Supplier;
import com.grocery.repository.InventoryBatchRepository;
import com.grocery.repository.InventoryTransactionRepository;
import com.grocery.repository.NotificationRepository;
import com.grocery.repository.ProductRepository;
import com.grocery.repository.SupplierRepository;
import com.grocery.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
public class InventoryController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);
    private static final Set<String> ALLOWED_ADJUSTMENT_TYPES = Set.of("ADJUSTMENT", "DAMAGE", "EXPIRY", "INTERNAL_USE");

    private final ProductRepository productRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final NotificationRepository notificationRepository;
    private final SupplierRepository supplierRepository;
    private final TenantContext tenantContext;
    private final MongoTemplate mongoTemplate;

    public InventoryController(ProductRepository productRepository,
                               InventoryBatchRepository batchRepository,
                               InventoryTransactionRepository transactionRepository,
                               NotificationRepository notificationRepository,
                               SupplierRepository supplierRepository,
                               TenantContext tenantContext,
                               MongoTemplate mongoTemplate) {
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.notificationRepository = notificationRepository;
        this.supplierRepository = supplierRepository;
        this.tenantContext = tenantContext;
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping
    public ResponseEntity<List<Product>> getInventoryOverview(@AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return ResponseEntity.ok(productRepository.findByStoreId(storeId));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<Product>> getLowStockProducts(@AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return ResponseEntity.ok(productRepository.findLowStockProductsByStore(storeId));
    }

    @GetMapping("/expiring")
    public ResponseEntity<List<InventoryBatch>> getExpiringBatches(
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        LocalDate now = LocalDate.now();
        LocalDate future = now.plusDays(days);
        return ResponseEntity.ok(batchRepository.findByStoreIdAndExpiryDateBetweenAndStatus(storeId, now, future, "ACTIVE"));
    }

    @GetMapping("/batches")
    public ResponseEntity<List<InventoryBatch>> getAllBatches(@AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return ResponseEntity.ok(batchRepository.findByStoreId(storeId));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<InventoryTransaction>> getAuditTransactions(@AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return ResponseEntity.ok(transactionRepository.findTop50ByStoreIdOrderByCreatedAtDesc(storeId));
    }

    @PostMapping("/adjustments")
    public ResponseEntity<ApiResponse<InventoryTransaction>> recordAdjustment(
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        String username = tenantContext.requireUsername(userDetails);

        if (payload == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Adjustment request payload is required"));
        }

        Object rawProductId = payload.get("productId");
        if (!(rawProductId instanceof String) || ((String) rawProductId).trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Product ID is required"));
        }
        String productId = ((String) rawProductId).trim();

        Object rawType = payload.get("type");
        String adjustmentType = "ADJUSTMENT";
        if (rawType != null) {
            if (!(rawType instanceof String)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid adjustment type"));
            }
            String candidate = ((String) rawType).trim().toUpperCase();
            if (!candidate.isEmpty()) {
                if (!ALLOWED_ADJUSTMENT_TYPES.contains(candidate)) {
                    return ResponseEntity.badRequest().body(ApiResponse.error(
                            "Unsupported adjustment type. Allowed: ADJUSTMENT, DAMAGE, EXPIRY, INTERNAL_USE"));
                }
                adjustmentType = candidate;
            }
        }

        Object rawQty = payload.get("quantity");
        if (rawQty == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Adjustment quantity is required"));
        }
        double quantity;
        try {
            quantity = Double.parseDouble(rawQty.toString().trim());
        } catch (NumberFormatException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Adjustment quantity must be a valid number"));
        }
        if (Double.isNaN(quantity) || Double.isInfinite(quantity) || quantity == 0.0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Adjustment quantity must be a non-zero finite number"));
        }

        Object rawNotes = payload.get("notes");
        String reason = "Physical stock adjustment";
        if (rawNotes != null) {
            String trimmedNotes = rawNotes.toString().trim();
            if (!trimmedNotes.isEmpty()) {
                reason = trimmedNotes;
            }
        }

        Product existingProduct = productRepository.findByStoreIdAndId(storeId, productId).orElse(null);
        if (existingProduct == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Product not found in your store"));
        }
        if ("ARCHIVED".equalsIgnoreCase(existingProduct.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Archived product cannot be adjusted"));
        }

        LocalDateTime now = LocalDateTime.now();
        Criteria criteria = Criteria.where("id").is(productId)
                .and("storeId").is(storeId)
                .and("status").ne("ARCHIVED");
        if (quantity < 0) {
            criteria = criteria.and("currentStock").gte(Math.abs(quantity));
        }

        Query stockQuery = new Query(criteria);
        Update stockUpdate = new Update()
                .inc("currentStock", quantity)
                .set("updatedAt", now);

        Product updatedProduct = mongoTemplate.findAndModify(
                stockQuery,
                stockUpdate,
                FindAndModifyOptions.options().returnNew(true),
                Product.class);

        if (updatedProduct == null) {
            Product latest = productRepository.findByStoreIdAndId(storeId, productId).orElse(null);
            if (latest == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Product not found in your store"));
            }
            if ("ARCHIVED".equalsIgnoreCase(latest.getStatus())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Archived product cannot be adjusted"));
            }
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Insufficient stock for adjustment. Requested reduction: " + Math.abs(quantity) + ", Available: " + latest.getCurrentStock()));
        }

        double newStock = updatedProduct.getCurrentStock();
        InventoryTransaction tx = new InventoryTransaction(
                updatedProduct.getId(),
                updatedProduct.getName(),
                adjustmentType,
                quantity,
                newStock,
                "ADJUSTMENT",
                "ADJ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                username,
                reason
        );
        tx.setStoreId(storeId);

        InventoryTransaction savedTx;
        try {
            savedTx = transactionRepository.save(tx);
        } catch (Exception ex) {
            logger.error("Audit ledger save failed during adjustment for store {}, product {}, qty {}: {}",
                    storeId, productId, quantity, ex.getMessage(), ex);
            boolean rolledBack = rollbackStockIncrement(storeId, productId, quantity, now);
            String msg = rolledBack
                    ? "Adjustment failed while recording audit ledger; stock change was rolled back"
                    : "Adjustment failed while recording audit ledger and stock rollback failed; manual reconciliation required";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(msg));
        }

        if (newStock <= updatedProduct.getMinStockLevel()) {
            try {
                Notification notif = new Notification(
                        "Low Stock Alert: " + updatedProduct.getName(),
                        "Stock adjusted to " + newStock + " " + updatedProduct.getUnit() + " (Min: " + updatedProduct.getMinStockLevel() + ")",
                        "LOW_STOCK",
                        "HIGH",
                        "inventory.html"
                );
                notif.setStoreId(storeId);
                notificationRepository.save(notif);
            } catch (Exception notifEx) {
                logger.warn("Non-critical low-stock notification failed after adjustment for store {}, product {}: {}",
                        storeId, productId, notifEx.getMessage());
            }
        }

        return ResponseEntity.ok(ApiResponse.success("Inventory adjusted successfully", savedTx));
    }

    @PostMapping("/restock")
    public ResponseEntity<ApiResponse<Map<String, Object>>> restockInventory(
            @RequestBody StockInRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        String username = tenantContext.requireUsername(userDetails);

        // 1. Request validation
        if (req == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Restock request payload is required"));
        }
        if (req.getProductId() == null || req.getProductId().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Product ID is required"));
        }
        String productId = req.getProductId().trim();

        if (req.getQuantity() == null
                || Double.isNaN(req.getQuantity())
                || Double.isInfinite(req.getQuantity())
                || req.getQuantity() <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Restock quantity must be a finite number strictly greater than zero"));
        }
        double quantity = req.getQuantity();

        if (req.getPurchaseCost() != null
                && (Double.isNaN(req.getPurchaseCost())
                || Double.isInfinite(req.getPurchaseCost())
                || req.getPurchaseCost() < 0)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Purchase cost must be a non-negative finite number"));
        }

        String supplierId = null;
        if (req.getSupplierId() != null && !req.getSupplierId().trim().isEmpty()) {
            supplierId = req.getSupplierId().trim();
        }

        String requestedBatchNumber = null;
        if (req.getBatchNumber() != null && !req.getBatchNumber().trim().isEmpty()) {
            requestedBatchNumber = req.getBatchNumber().trim();
        }

        String notes = (req.getNotes() != null && !req.getNotes().trim().isEmpty())
                ? req.getNotes().trim()
                : "Direct stock replenishment";

        String idemKey = (req.getIdempotencyKey() != null && !req.getIdempotencyKey().trim().isEmpty())
                ? req.getIdempotencyKey().trim()
                : null;
        String fingerprint = computeRestockFingerprint(
                productId, quantity, req.getPurchaseCost(), supplierId, requestedBatchNumber, req.getExpiryDate(), notes);

        // 2. Idempotency fast-path check (scoped to authenticated store)
        if (idemKey != null) {
            InventoryTransaction existingTx = transactionRepository
                    .findByStoreIdAndIdempotencyKey(storeId, idemKey)
                    .orElse(null);
            if (existingTx != null) {
                if (existingTx.getIdempotencyFingerprint() != null
                        && !existingTx.getIdempotencyFingerprint().equals(fingerprint)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(ApiResponse.error("Idempotency conflict: key '" + idemKey + "' was already used with different restock parameters"));
                }
                return ResponseEntity.ok(ApiResponse.success(
                        "Stock replenished successfully (idempotent)",
                        buildRestockResponseData(storeId, existingTx)));
            }
        }

        // 3. Product existence, tenant ownership, and status check
        Product existingProduct = productRepository.findByStoreIdAndId(storeId, productId).orElse(null);
        if (existingProduct == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Product not found in your store"));
        }
        if ("ARCHIVED".equalsIgnoreCase(existingProduct.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Archived product cannot be restocked"));
        }

        // 4. Supplier validation (tenant-scoped, server-authoritative name)
        String supplierName = null;
        if (supplierId != null) {
            Supplier supplier = supplierRepository.findByStoreIdAndId(storeId, supplierId).orElse(null);
            if (supplier == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Supplier not found in your store"));
            }
            supplierId = supplier.getId();
            supplierName = supplier.getName();
        }

        // 5. Batch number resolution (collision-resistant or validated custom batch number)
        String batchNumber;
        if (requestedBatchNumber != null) {
            if (batchRepository.existsByStoreIdAndBatchNumber(storeId, requestedBatchNumber)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Batch number already exists in your store"));
            }
            batchNumber = requestedBatchNumber;
        } else {
            batchNumber = generateUniqueBatchNumber(storeId);
        }

        double effectivePurchaseCost = req.getPurchaseCost() != null
                ? req.getPurchaseCost()
                : existingProduct.getCostPrice();

        LocalDateTime now = LocalDateTime.now();

        // 6. Atomic product stock increment
        Query stockQuery = new Query(Criteria.where("id").is(productId)
                .and("storeId").is(storeId)
                .and("status").ne("ARCHIVED"));
        Update stockUpdate = new Update()
                .inc("currentStock", quantity)
                .set("updatedAt", now);

        Product updatedProduct = mongoTemplate.findAndModify(
                stockQuery,
                stockUpdate,
                FindAndModifyOptions.options().returnNew(true),
                Product.class);

        if (updatedProduct == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Product is not eligible for restock"));
        }

        // 7. Create InventoryBatch (with compensation if save fails)
        InventoryBatch batch = new InventoryBatch(
                updatedProduct.getId(),
                updatedProduct.getName(),
                batchNumber,
                supplierId,
                supplierName,
                LocalDate.now(),
                req.getExpiryDate(),
                quantity,
                quantity,
                effectivePurchaseCost
        );
        batch.setStoreId(storeId);

        InventoryBatch savedBatch;
        try {
            savedBatch = batchRepository.save(batch);
        } catch (DuplicateKeyException dke) {
            boolean rolledBack = rollbackStockIncrement(storeId, productId, quantity, now);
            if (!rolledBack) {
                logger.error("CRITICAL: Stock rollback failed after batch DuplicateKeyException for store {}, product {}, batchNumber {}",
                        storeId, productId, batchNumber);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("Duplicate batch number conflict and stock rollback failed; manual reconciliation required"));
            }
            if (idemKey != null) {
                InventoryTransaction existingTx = transactionRepository
                        .findByStoreIdAndIdempotencyKey(storeId, idemKey)
                        .orElse(null);
                if (existingTx != null) {
                    if (existingTx.getIdempotencyFingerprint() != null
                            && !existingTx.getIdempotencyFingerprint().equals(fingerprint)) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error("Idempotency conflict: key '" + idemKey + "' was already used with different restock parameters"));
                    }
                    return ResponseEntity.ok(ApiResponse.success(
                            "Stock replenished successfully (idempotent)",
                            buildRestockResponseData(storeId, existingTx)));
                }
            }
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Batch number already exists in your store; stock increase was rolled back"));
        } catch (Exception ex) {
            logger.error("Batch save failed during restock for store {}, product {}, qty {}: {}",
                    storeId, productId, quantity, ex.getMessage(), ex);
            boolean rolledBack = rollbackStockIncrement(storeId, productId, quantity, now);
            String msg = rolledBack
                    ? "Restock failed while creating inventory batch; stock increase was rolled back"
                    : "Restock failed while creating inventory batch and stock rollback failed; manual reconciliation required";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(msg));
        }

        // 8. Create InventoryTransaction ledger entry (with compensation + idempotency race handling)
        InventoryTransaction tx = new InventoryTransaction(
                updatedProduct.getId(),
                updatedProduct.getName(),
                "RESTOCK",
                quantity,
                updatedProduct.getCurrentStock(),
                "DIRECT_RESTOCK",
                savedBatch.getBatchNumber(),
                username,
                notes
        );
        tx.setStoreId(storeId);
        tx.setIdempotencyKey(idemKey);
        tx.setIdempotencyFingerprint(idemKey != null ? fingerprint : null);

        InventoryTransaction savedTx;
        try {
            savedTx = transactionRepository.save(tx);
        } catch (DuplicateKeyException dke) {
            boolean stockRolledBack = rollbackStockIncrement(storeId, productId, quantity, now);
            boolean batchCleaned = deleteBatchQuietly(storeId, savedBatch.getId());
            if (!stockRolledBack || !batchCleaned) {
                logger.error("CRITICAL: Partial compensation failure during concurrent idempotent restock for store {}, product {}, batch {}",
                        storeId, productId, savedBatch.getId());
            }

            if (idemKey != null) {
                InventoryTransaction existingTx = transactionRepository
                        .findByStoreIdAndIdempotencyKey(storeId, idemKey)
                        .orElse(null);
                if (existingTx != null) {
                    if (existingTx.getIdempotencyFingerprint() != null
                            && !existingTx.getIdempotencyFingerprint().equals(fingerprint)) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error("Idempotency conflict: key '" + idemKey + "' was already used with different restock parameters"));
                    }
                    return ResponseEntity.ok(ApiResponse.success(
                            "Stock replenished successfully (idempotent)",
                            buildRestockResponseData(storeId, existingTx)));
                }
            }
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Concurrent duplicate restock request detected"));
        } catch (Exception ex) {
            logger.error("Transaction ledger save failed during restock for store {}, product {}, batch {}: {}",
                    storeId, productId, savedBatch.getId(), ex.getMessage(), ex);
            boolean stockRolledBack = rollbackStockIncrement(storeId, productId, quantity, now);
            boolean batchCleaned = deleteBatchQuietly(storeId, savedBatch.getId());
            String msg = (stockRolledBack && batchCleaned)
                    ? "Restock failed while recording inventory transaction; stock and batch changes were rolled back"
                    : "Restock failed while recording inventory transaction and compensation was incomplete (stockRolledBack="
                            + stockRolledBack + ", batchCleaned=" + batchCleaned + "); manual reconciliation required";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(msg));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("product", updatedProduct);
        data.put("batch", savedBatch);
        data.put("transaction", savedTx);
        return ResponseEntity.ok(ApiResponse.success("Stock replenished successfully", data));
    }

    private String generateUniqueBatchNumber(String storeId) {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 0; i < 5; i++) {
            String candidate = "BATCH-" + datePart + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            if (!batchRepository.existsByStoreIdAndBatchNumber(storeId, candidate)) {
                return candidate;
            }
        }
        return "BATCH-" + datePart + "-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private boolean rollbackStockIncrement(String storeId, String productId, double quantity, LocalDateTime now) {
        try {
            Query rollbackQuery = new Query(Criteria.where("id").is(productId).and("storeId").is(storeId));
            Update rollbackUpdate = new Update().inc("currentStock", -quantity).set("updatedAt", now);
            var result = mongoTemplate.updateFirst(rollbackQuery, rollbackUpdate, Product.class);
            if (result.getModifiedCount() == 0) {
                logger.error("CRITICAL: Stock rollback modified 0 documents for store {}, product {}, qty {}",
                        storeId, productId, quantity);
                return false;
            }
            return true;
        } catch (Exception ex) {
            logger.error("CRITICAL: Exception during stock rollback for store {}, product {}, qty {}: {}",
                    storeId, productId, quantity, ex.getMessage(), ex);
            return false;
        }
    }

    private boolean deleteBatchQuietly(String storeId, String batchId) {
        if (batchId == null) {
            return true;
        }
        try {
            Query deleteQuery = new Query(Criteria.where("id").is(batchId).and("storeId").is(storeId));
            var result = mongoTemplate.remove(deleteQuery, InventoryBatch.class);
            if (result.getDeletedCount() == 0) {
                logger.error("CRITICAL: Batch cleanup deleted 0 documents for store {}, batchId {}", storeId, batchId);
                return false;
            }
            return true;
        } catch (Exception ex) {
            logger.error("CRITICAL: Exception during batch cleanup for store {}, batchId {}: {}",
                    storeId, batchId, ex.getMessage(), ex);
            return false;
        }
    }

    private Map<String, Object> buildRestockResponseData(String storeId, InventoryTransaction existingTx) {
        Map<String, Object> data = new HashMap<>();
        productRepository.findByStoreIdAndId(storeId, existingTx.getProductId())
                .ifPresent(p -> data.put("product", p));
        if (existingTx.getReferenceId() != null) {
            batchRepository.findByStoreIdAndBatchNumber(storeId, existingTx.getReferenceId())
                    .ifPresent(b -> data.put("batch", b));
        }
        data.put("transaction", existingTx);
        return data;
    }

    private String computeRestockFingerprint(String productId,
                                             double quantity,
                                             Double purchaseCost,
                                             String supplierId,
                                             String batchNumber,
                                             LocalDate expiryDate,
                                             String notes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = String.join("|",
                    productId,
                    Double.toString(quantity),
                    purchaseCost != null ? Double.toString(purchaseCost) : "",
                    supplierId != null ? supplierId : "",
                    batchNumber != null ? batchNumber : "",
                    expiryDate != null ? expiryDate.toString() : "",
                    notes != null ? notes : "");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return productId + ":" + quantity;
        }
    }
}
