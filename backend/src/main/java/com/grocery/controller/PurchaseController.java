package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.*;
import com.grocery.repository.*;
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
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/purchases")
@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
public class PurchaseController {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseController.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final TenantContext tenantContext;
    private final MongoTemplate mongoTemplate;

    public PurchaseController(PurchaseOrderRepository purchaseOrderRepository,
                              ProductRepository productRepository,
                              SupplierRepository supplierRepository,
                              InventoryBatchRepository batchRepository,
                              InventoryTransactionRepository transactionRepository,
                              TenantContext tenantContext,
                              MongoTemplate mongoTemplate) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.tenantContext = tenantContext;
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping
    public ResponseEntity<List<PurchaseOrder>> getPurchaseOrders(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        if (status != null && !status.isEmpty()) {
            return ResponseEntity.ok(purchaseOrderRepository.findByStoreIdAndStatusOrderByCreatedAtDesc(storeId, status));
        }
        return ResponseEntity.ok(purchaseOrderRepository.findByStoreIdOrderByCreatedAtDesc(storeId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseOrder>> createPurchaseOrder(
            @RequestBody PurchaseOrder po,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);

        if (po == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Purchase order payload is required"));
        }

        // Validate supplier ownership if supplierId is specified
        if (po.getSupplierId() != null && !po.getSupplierId().trim().isEmpty()) {
            String cleanSupId = po.getSupplierId().trim();
            Supplier sup = supplierRepository.findByStoreIdAndId(storeId, cleanSupId).orElse(null);
            if (sup == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Supplier does not belong to your store"));
            }
            po.setSupplierId(sup.getId());
            po.setSupplierName(sup.getName());
        } else {
            po.setSupplierId(null);
        }

        if (po.getItems() == null || po.getItems().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Purchase order must contain at least one item"));
        }

        // Validate all referenced products belong to the same store, are not archived, and have valid qty/cost
        for (PurchaseOrder.PurchaseOrderItem item : po.getItems()) {
            if (item.getProductId() == null || item.getProductId().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Each purchase item must reference a valid productId"));
            }
            String cleanProdId = item.getProductId().trim();
            item.setProductId(cleanProdId);

            Product prod = productRepository.findByStoreIdAndId(storeId, cleanProdId).orElse(null);
            if (prod == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Product does not belong to your store: " + cleanProdId));
            }
            if ("ARCHIVED".equalsIgnoreCase(prod.getStatus())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Archived product cannot be ordered: " + prod.getName()));
            }
            if (Double.isNaN(item.getOrderQty()) || Double.isInfinite(item.getOrderQty()) || item.getOrderQty() <= 0) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Order quantity must be finite and greater than zero for: " + prod.getName()));
            }
            if (Double.isNaN(item.getUnitCost()) || Double.isInfinite(item.getUnitCost()) || item.getUnitCost() < 0) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Unit cost must be a non-negative finite number for: " + prod.getName()));
            }
            item.setProductName(prod.getName());
            item.setTotalCost(item.getOrderQty() * item.getUnitCost());
        }

        po.setId(null);
        po.setStoreId(storeId);
        if (po.getOrderNumber() == null || po.getOrderNumber().trim().isEmpty()) {
            po.setOrderNumber("PO-" + LocalDate.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        } else {
            po.setOrderNumber(po.getOrderNumber().trim());
        }
        po.setStatus("DRAFT");
        po.setReceiveIdempotencyKey(null);
        po.setReceiveIdempotencyFingerprint(null);
        po.setReceivedAt(null);
        po.setReceivedBy(null);
        po.setCreatedAt(LocalDateTime.now());
        double total = po.getItems().stream().mapToDouble(PurchaseOrder.PurchaseOrderItem::getTotalCost).sum();
        po.setTotalAmount(total);

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return ResponseEntity.ok(ApiResponse.success("Purchase order created", saved));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<PurchaseOrder>> submitPurchaseOrder(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        PurchaseOrder existing = purchaseOrderRepository.findByStoreIdAndId(storeId, id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"DRAFT".equalsIgnoreCase(existing.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Only DRAFT purchase orders can be submitted. Current status: " + existing.getStatus()));
        }
        existing.setStatus("ORDERED");
        PurchaseOrder saved = purchaseOrderRepository.save(existing);
        return ResponseEntity.ok(ApiResponse.success("Purchase order marked as ORDERED with supplier", saved));
    }

    @PostMapping("/{id}/receive")
    public ResponseEntity<ApiResponse<PurchaseOrder>> receiveGoods(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> payload,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        String username = tenantContext.requireUsername(userDetails);

        // 1. Parse optional payload (idempotencyKey, expiryDate)
        String idemKey = null;
        LocalDate requestedExpiryDate = null;
        if (payload != null) {
            Object rawKey = payload.get("idempotencyKey");
            if (rawKey != null && !rawKey.toString().trim().isEmpty()) {
                idemKey = rawKey.toString().trim();
            }
            Object rawExpiry = payload.get("expiryDate");
            if (rawExpiry != null && !rawExpiry.toString().trim().isEmpty()) {
                try {
                    requestedExpiryDate = LocalDate.parse(rawExpiry.toString().trim());
                } catch (DateTimeParseException ex) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Invalid expiryDate format. Expected YYYY-MM-DD"));
                }
            }
        }
        String fingerprint = computeReceiveFingerprint(id, requestedExpiryDate);

        // 2. Fast-path idempotency check (store-scoped)
        if (idemKey != null) {
            PurchaseOrder existingByKey = purchaseOrderRepository
                    .findByStoreIdAndReceiveIdempotencyKey(storeId, idemKey)
                    .orElse(null);
            if (existingByKey != null) {
                if (!id.equals(existingByKey.getId())) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(ApiResponse.error("Idempotency conflict: key '" + idemKey + "' was already used for another purchase order"));
                }
                if (existingByKey.getReceiveIdempotencyFingerprint() != null
                        && !existingByKey.getReceiveIdempotencyFingerprint().equals(fingerprint)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(ApiResponse.error("Idempotency conflict: key '" + idemKey + "' was already used with different receive parameters"));
                }
                return ResponseEntity.ok(ApiResponse.success("Purchase order already received and stocked (idempotent)", existingByKey));
            }
        }

        // 3. Load and validate PO existence, tenant ownership, and status
        PurchaseOrder po = purchaseOrderRepository.findByStoreIdAndId(storeId, id).orElse(null);
        if (po == null) {
            return ResponseEntity.notFound().build();
        }
        if ("RECEIVED".equalsIgnoreCase(po.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Purchase order already received and stocked"));
        }
        if ("RECEIVING".equalsIgnoreCase(po.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Purchase order receiving is already in progress"));
        }
        if (!"ORDERED".equalsIgnoreCase(po.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Only ORDERED purchase orders can be received. Current status: " + po.getStatus()));
        }

        // 4. Validate supplier and all line items BEFORE mutating any state
        String supplierId = null;
        String supplierName = po.getSupplierName();
        if (po.getSupplierId() != null && !po.getSupplierId().trim().isEmpty()) {
            Supplier supplier = supplierRepository.findByStoreIdAndId(storeId, po.getSupplierId().trim()).orElse(null);
            if (supplier == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Supplier does not belong to your store"));
            }
            supplierId = supplier.getId();
            supplierName = supplier.getName();
        }

        if (po.getItems() == null || po.getItems().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Purchase order has no items to receive"));
        }

        Map<String, Product> validatedProducts = new HashMap<>();
        for (PurchaseOrder.PurchaseOrderItem item : po.getItems()) {
            if (item.getProductId() == null || item.getProductId().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Purchase item has missing productId"));
            }
            String prodId = item.getProductId().trim();
            if (Double.isNaN(item.getOrderQty()) || Double.isInfinite(item.getOrderQty()) || item.getOrderQty() <= 0) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Receiving quantity must be finite and greater than zero"));
            }
            if (Double.isNaN(item.getUnitCost()) || Double.isInfinite(item.getUnitCost()) || item.getUnitCost() < 0) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Unit cost must be a non-negative finite number"));
            }
            Product prod = validatedProducts.computeIfAbsent(prodId,
                    pid -> productRepository.findByStoreIdAndId(storeId, pid).orElse(null));
            if (prod == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Product does not belong to your store: " + prodId));
            }
            if ("ARCHIVED".equalsIgnoreCase(prod.getStatus())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Archived product cannot be received: " + prod.getName()));
            }
        }

        // 5. Atomically claim PO transition ORDERED -> RECEIVING to prevent concurrent duplicate receiving
        Query claimQuery = new Query(Criteria.where("id").is(id).and("storeId").is(storeId).and("status").is("ORDERED"));
        Update claimUpdate = new Update().set("status", "RECEIVING");
        PurchaseOrder claimedPo = mongoTemplate.findAndModify(
                claimQuery,
                claimUpdate,
                FindAndModifyOptions.options().returnNew(true),
                PurchaseOrder.class);

        if (claimedPo == null) {
            PurchaseOrder latest = purchaseOrderRepository.findByStoreIdAndId(storeId, id).orElse(null);
            if (latest != null && idemKey != null && idemKey.equals(latest.getReceiveIdempotencyKey())) {
                if (latest.getReceiveIdempotencyFingerprint() != null && !latest.getReceiveIdempotencyFingerprint().equals(fingerprint)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(ApiResponse.error("Idempotency conflict: key '" + idemKey + "' was already used with different receive parameters"));
                }
                return ResponseEntity.ok(ApiResponse.success("Purchase order already received and stocked (idempotent)", latest));
            }
            if (latest != null && "RECEIVED".equalsIgnoreCase(latest.getStatus())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Purchase order already received and stocked"));
            }
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Purchase order receiving is already in progress or status changed concurrently"));
        }

        // 6. Execute stock increments, batch creation, ledger creation, and final PO update with compensation
        LocalDateTime now = LocalDateTime.now();
        Map<String, Double> incrementedStock = new LinkedHashMap<>();
        List<String> createdBatchIds = new ArrayList<>();
        List<String> createdTxIds = new ArrayList<>();

        try {
            for (PurchaseOrder.PurchaseOrderItem item : claimedPo.getItems()) {
                String prodId = item.getProductId().trim();
                double qty = item.getOrderQty();

                Query stockQuery = new Query(Criteria.where("id").is(prodId)
                        .and("storeId").is(storeId)
                        .and("status").ne("ARCHIVED"));
                Update stockUpdate = new Update()
                        .inc("currentStock", qty)
                        .set("updatedAt", now);

                Product updatedProduct = mongoTemplate.findAndModify(
                        stockQuery,
                        stockUpdate,
                        FindAndModifyOptions.options().returnNew(true),
                        Product.class);

                if (updatedProduct == null) {
                    throw new IllegalStateException("Product is no longer eligible for receiving: " + prodId);
                }
                incrementedStock.merge(prodId, qty, Double::sum);

                String batchNum = generateUniqueBatchNumber(storeId);
                InventoryBatch batch = new InventoryBatch(
                        updatedProduct.getId(),
                        updatedProduct.getName(),
                        batchNum,
                        supplierId,
                        supplierName,
                        LocalDate.now(),
                        requestedExpiryDate,
                        qty,
                        qty,
                        item.getUnitCost()
                );
                batch.setStoreId(storeId);
                InventoryBatch savedBatch = batchRepository.save(batch);
                if (savedBatch.getId() != null) {
                    createdBatchIds.add(savedBatch.getId());
                }

                InventoryTransaction tx = new InventoryTransaction(
                        updatedProduct.getId(),
                        updatedProduct.getName(),
                        "PURCHASE_RECEIPT",
                        qty,
                        updatedProduct.getCurrentStock(),
                        "PURCHASE_ORDER",
                        claimedPo.getOrderNumber(),
                        username,
                        "Goods received from PO: " + claimedPo.getOrderNumber() + " (Batch: " + savedBatch.getBatchNumber() + ")"
                );
                tx.setStoreId(storeId);
                InventoryTransaction savedTx = transactionRepository.save(tx);
                if (savedTx.getId() != null) {
                    createdTxIds.add(savedTx.getId());
                }
            }

            claimedPo.setStatus("RECEIVED");
            claimedPo.setReceivedAt(now);
            claimedPo.setReceivedBy(username);
            claimedPo.setReceiveIdempotencyKey(idemKey);
            claimedPo.setReceiveIdempotencyFingerprint(idemKey != null ? fingerprint : null);
            PurchaseOrder finalizedPo = purchaseOrderRepository.save(claimedPo);

            return ResponseEntity.ok(ApiResponse.success("Goods received and stock inwarded successfully", finalizedPo));

        } catch (DuplicateKeyException dke) {
            boolean allCompensated = compensateFailedReceive(storeId, id, incrementedStock, createdBatchIds, createdTxIds, now);
            if (idemKey != null) {
                PurchaseOrder existingByKey = purchaseOrderRepository
                        .findByStoreIdAndReceiveIdempotencyKey(storeId, idemKey)
                        .orElse(null);
                if (existingByKey != null) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(ApiResponse.error("Idempotency conflict: key '" + idemKey + "' was already used"));
                }
            }
            String msg = allCompensated
                    ? "Duplicate key conflict during purchase receiving; changes were rolled back"
                    : "Duplicate key conflict during purchase receiving and compensation was incomplete; manual reconciliation required";
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(msg));

        } catch (Exception ex) {
            logger.error("Purchase receiving failed for store {}, poId {}: {}", storeId, id, ex.getMessage(), ex);
            boolean allCompensated = compensateFailedReceive(storeId, id, incrementedStock, createdBatchIds, createdTxIds, now);
            String msg = allCompensated
                    ? "Purchase receiving failed; stock, batches, ledger, and PO status were rolled back"
                    : "Purchase receiving failed and compensation was incomplete; manual reconciliation required";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(msg));
        }
    }

    private boolean compensateFailedReceive(String storeId,
                                            String poId,
                                            Map<String, Double> incrementedStock,
                                            List<String> createdBatchIds,
                                            List<String> createdTxIds,
                                            LocalDateTime now) {
        boolean allOk = true;

        for (Map.Entry<String, Double> entry : incrementedStock.entrySet()) {
            try {
                Query q = new Query(Criteria.where("id").is(entry.getKey()).and("storeId").is(storeId));
                Update u = new Update().inc("currentStock", -entry.getValue()).set("updatedAt", now);
                var res = mongoTemplate.updateFirst(q, u, Product.class);
                if (res.getModifiedCount() == 0) {
                    logger.error("CRITICAL: PO receive stock rollback modified 0 docs for store {}, product {}, qty {}",
                            storeId, entry.getKey(), entry.getValue());
                    allOk = false;
                }
            } catch (Exception e) {
                logger.error("CRITICAL: PO receive stock rollback exception for store {}, product {}: {}",
                        storeId, entry.getKey(), e.getMessage(), e);
                allOk = false;
            }
        }

        if (!createdBatchIds.isEmpty()) {
            try {
                Query q = new Query(Criteria.where("id").in(createdBatchIds).and("storeId").is(storeId));
                var res = mongoTemplate.remove(q, InventoryBatch.class);
                if (res.getDeletedCount() != createdBatchIds.size()) {
                    logger.error("CRITICAL: PO receive batch cleanup deleted {} of {} docs for store {}, batchIds {}",
                            res.getDeletedCount(), createdBatchIds.size(), storeId, createdBatchIds);
                    allOk = false;
                }
            } catch (Exception e) {
                logger.error("CRITICAL: PO receive batch cleanup exception for store {}, batchIds {}: {}",
                        storeId, createdBatchIds, e.getMessage(), e);
                allOk = false;
            }
        }

        if (!createdTxIds.isEmpty()) {
            try {
                Query q = new Query(Criteria.where("id").in(createdTxIds).and("storeId").is(storeId));
                var res = mongoTemplate.remove(q, InventoryTransaction.class);
                if (res.getDeletedCount() != createdTxIds.size()) {
                    logger.error("CRITICAL: PO receive ledger cleanup deleted {} of {} docs for store {}, txIds {}",
                            res.getDeletedCount(), createdTxIds.size(), storeId, createdTxIds);
                    allOk = false;
                }
            } catch (Exception e) {
                logger.error("CRITICAL: PO receive ledger cleanup exception for store {}, txIds {}: {}",
                        storeId, createdTxIds, e.getMessage(), e);
                allOk = false;
            }
        }

        try {
            Query q = new Query(Criteria.where("id").is(poId).and("storeId").is(storeId).and("status").is("RECEIVING"));
            Update u = new Update()
                    .set("status", "ORDERED")
                    .unset("receiveIdempotencyKey")
                    .unset("receiveIdempotencyFingerprint")
                    .unset("receivedAt")
                    .unset("receivedBy");
            var res = mongoTemplate.updateFirst(q, u, PurchaseOrder.class);
            if (res.getModifiedCount() == 0) {
                logger.error("CRITICAL: PO status revert modified 0 docs for store {}, poId {}", storeId, poId);
                allOk = false;
            }
        } catch (Exception e) {
            logger.error("CRITICAL: PO status revert exception for store {}, poId {}: {}", storeId, poId, e.getMessage(), e);
            allOk = false;
        }

        return allOk;
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

    private String computeReceiveFingerprint(String poId, LocalDate expiryDate) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = poId + "|" + (expiryDate != null ? expiryDate.toString() : "");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return poId;
        }
    }
}
