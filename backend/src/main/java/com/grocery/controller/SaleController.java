package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.*;
import com.grocery.repository.*;
import com.grocery.security.TenantContext;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','CASHIER')")
public class SaleController {

    private static final Logger logger = LoggerFactory.getLogger(SaleController.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private volatile long reservationGracePeriodMs = 30_000L;

    private final SaleRepository saleRepository;
    private final ReturnRepository returnRepository;
    private final ProductRepository productRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final NotificationRepository notificationRepository;
    private final TenantContext tenantContext;
    private final MongoTemplate mongoTemplate;

    public SaleController(SaleRepository saleRepository,
                          ReturnRepository returnRepository,
                          ProductRepository productRepository,
                          InventoryBatchRepository batchRepository,
                          InventoryTransactionRepository transactionRepository,
                          CustomerRepository customerRepository,
                          NotificationRepository notificationRepository,
                          TenantContext tenantContext,
                          MongoTemplate mongoTemplate) {
        this.saleRepository = saleRepository;
        this.returnRepository = returnRepository;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
        this.notificationRepository = notificationRepository;
        this.tenantContext = tenantContext;
        this.mongoTemplate = mongoTemplate;
    }

    @PostMapping("/sales")
    public ResponseEntity<ApiResponse<Sale>> processCheckout(
            @RequestBody Sale sale,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        String username = tenantContext.requireUsername(userDetails);

        if (sale.getItems() == null || sale.getItems().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Sale must contain at least one item"));
        }

        // Compute authoritative payload fingerprint for idempotency conflict protection
        String requestFingerprint = computePayloadFingerprint(sale);

        // 1. Check idempotency if idempotencyKey is supplied
        String idempotencyKey = sale.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            idempotencyKey = idempotencyKey.trim();
            sale.setIdempotencyKey(idempotencyKey);
            sale.setIdempotencyFingerprint(requestFingerprint);
            java.util.Optional<Sale> existingSaleOpt = saleRepository.findByStoreIdAndIdempotencyKey(storeId, idempotencyKey);
            if (existingSaleOpt.isPresent()) {
                Sale existing = existingSaleOpt.get();
                // If fingerprint is recorded on existing sale and doesn't match current request, reject conflict!
                if (existing.getIdempotencyFingerprint() != null && !existing.getIdempotencyFingerprint().equals(requestFingerprint)) {
                    return ResponseEntity.status(409)
                            .body(ApiResponse.error("Idempotency conflict: key '" + idempotencyKey + "' has already been used with different checkout items or customer."));
                }
                return ResponseEntity.ok(ApiResponse.success("Checkout processed successfully (idempotent)", existing));
            }
        } else {
            sale.setIdempotencyKey(null);
            sale.setIdempotencyFingerprint(null);
        }

        // 2. Validate items, positive quantities, store ownership, and status BEFORE mutating stock
        Map<String, Product> verifiedProducts = new HashMap<>();
        Map<String, Double> aggregatedQuantities = new HashMap<>();

        for (Sale.SaleItem item : sale.getItems()) {
            if (item.getProductId() == null || item.getProductId().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Each sale item must reference a valid productId"));
            }
            String pId = item.getProductId().trim();
            item.setProductId(pId);

            if (item.getQuantity() <= 0) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Item quantity must be strictly greater than 0: " + pId));
            }

            if (!verifiedProducts.containsKey(pId)) {
                Product product = productRepository.findByStoreIdAndId(storeId, pId).orElse(null);
                if (product == null) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("Product does not belong to your store: " + pId));
                }
                if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("Inactive product cannot be sold: " + product.getName()));
                }
                verifiedProducts.put(pId, product);
            }

            // Aggregate duplicate cart lines for the same product to prevent stock boundary bypass
            aggregatedQuantities.put(pId, aggregatedQuantities.getOrDefault(pId, 0.0) + item.getQuantity());
        }

        // Validate referenced customerId belongs to the authenticated store
        Customer targetCustomer = null;
        if (sale.getCustomerId() != null && !sale.getCustomerId().trim().isEmpty()) {
            targetCustomer = customerRepository.findByStoreIdAndId(storeId, sale.getCustomerId().trim()).orElse(null);
            if (targetCustomer == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Customer does not belong to your store"));
            }
        } else if (sale.getCustomerPhone() != null && !sale.getCustomerPhone().trim().isEmpty()) {
            targetCustomer = customerRepository.findByStoreIdAndPhone(storeId, sale.getCustomerPhone().trim()).orElse(null);
        }

        Instant nowInstant = Instant.now();
        LocalDateTime nowIst = LocalDateTime.ofInstant(nowInstant, IST);
        sale.setStoreId(storeId);
        sale.setCashierName(username);
        sale.setCreatedAt(nowIst);
        sale.setCreatedAtInstant(nowInstant);
        sale.setStatus("COMPLETED");
        sale.setRefundedTotal(0.0);
        sale.setPendingRefundTotal(0.0);

        // 3. Atomic conditional stock deduction for each aggregated product
        // Ensures no overselling, concurrent race safety, and strictly positive stock
        Map<String, Double> successfullyDeducted = new HashMap<>();
        Map<String, Product> updatedProducts = new HashMap<>();

        for (Map.Entry<String, Double> entry : aggregatedQuantities.entrySet()) {
            String prodId = entry.getKey();
            Double requiredQty = entry.getValue();

            Query query = new Query(Criteria.where("id").is(prodId)
                    .and("storeId").is(storeId)
                    .and("status").is("ACTIVE")
                    .and("currentStock").gte(requiredQty));
            Update update = new Update().inc("currentStock", -requiredQty).set("updatedAt", nowIst);

            Product updated = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Product.class);
            if (updated == null) {
                // Stock deduction failed! Roll back any products already deducted in this request
                rollbackDeductedStock(storeId, successfullyDeducted, nowIst);

                // Fetch latest state to provide informative error message
                Product currentProd = productRepository.findByStoreIdAndId(storeId, prodId).orElse(verifiedProducts.get(prodId));
                double available = currentProd != null ? currentProd.getCurrentStock() : 0.0;
                String prodName = currentProd != null ? currentProd.getName() : prodId;
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Insufficient stock for product: " + prodName + ". Requested: " + requiredQty + ", Available: " + available));
            }

            successfullyDeducted.put(prodId, requiredQty);
            updatedProducts.put(prodId, updated);
        }

        // 4. Generate per-store sequential receipt number & server-authoritative cost snapshot
        sale.setReceiptNumber(generateNextReceiptNumber(storeId, nowIst.getYear()));
        List<InventoryTransaction> transactionsToSave = new ArrayList<>();
        List<Notification> notificationsToSave = new ArrayList<>();

        for (Sale.SaleItem item : sale.getItems()) {
            Product originalProduct = verifiedProducts.get(item.getProductId());
            Product updatedProduct = updatedProducts.get(item.getProductId());

            if (item.getSaleItemId() == null || item.getSaleItemId().trim().isEmpty()) {
                item.setSaleItemId(UUID.randomUUID().toString());
            }
            // Never trust client-supplied costPrice; never silently substitute 0 for missing product cost
            item.setCostPrice(originalProduct.getCostPrice() > 0 ? originalProduct.getCostPrice() : null);

            double remainingStock = updatedProduct != null ? updatedProduct.getCurrentStock() : 0.0;

            InventoryTransaction tx = new InventoryTransaction(
                    originalProduct.getId(),
                    originalProduct.getName(),
                    "SALE",
                    -item.getQuantity(),
                    remainingStock,
                    "POS_SALE",
                    sale.getReceiptNumber(),
                    username,
                    "POS Checkout item: " + originalProduct.getName()
            );
            tx.setStoreId(storeId);
            transactionsToSave.add(tx);
        }

        // Check low-stock threshold per unique product deducted
        for (Map.Entry<String, Product> prodEntry : updatedProducts.entrySet()) {
            Product prod = prodEntry.getValue();
            if (prod.getCurrentStock() <= prod.getMinStockLevel()) {
                Notification notif = new Notification(
                        "Low Stock Alert: " + prod.getName(),
                        "Stock decreased to " + prod.getCurrentStock() + " " + prod.getUnit() + " (Min: " + prod.getMinStockLevel() + ")",
                        "LOW_STOCK",
                        "HIGH",
                        "inventory.html"
                );
                notif.setStoreId(storeId);
                notificationsToSave.add(notif);
            }
        }

        // 5. Persist sale and inventory records with strict failure recovery
        List<String> persistedTxIds = new ArrayList<>();
        Integer originalLoyalty = targetCustomer != null ? targetCustomer.getLoyaltyPoints() : null;
        Double originalSpent = targetCustomer != null ? targetCustomer.getTotalSpent() : null;
        Integer originalOrders = targetCustomer != null ? targetCustomer.getOrdersCount() : null;
        String originalTier = targetCustomer != null ? targetCustomer.getTier() : null;
        boolean customerMutated = false;

        try {
            // A. Save inventory ledger entries
            List<InventoryTransaction> savedTxs = transactionRepository.saveAll(transactionsToSave);
            for (InventoryTransaction tx : savedTxs) {
                if (tx.getId() != null) persistedTxIds.add(tx.getId());
            }

            // B. Non-critical notifications (safeguarded against crashing checkout)
            if (!notificationsToSave.isEmpty()) {
                try {
                    notificationRepository.saveAll(notificationsToSave);
                } catch (Exception notifEx) {
                    logger.warn("Non-critical notification save failed during checkout for store {}: {}", storeId, notifEx.getMessage());
                }
            }

            // C. Update customer loyalty if customer attached
            if (targetCustomer != null) {
                int earnedPoints = (int)(sale.getGrandTotal() / 100);
                targetCustomer.setLoyaltyPoints(targetCustomer.getLoyaltyPoints() + earnedPoints);
                targetCustomer.setTotalSpent(targetCustomer.getTotalSpent() + sale.getGrandTotal());
                targetCustomer.setOrdersCount(targetCustomer.getOrdersCount() + 1);
                if (targetCustomer.getTotalSpent() > 20000) targetCustomer.setTier("GOLD");
                else if (targetCustomer.getTotalSpent() > 5000) targetCustomer.setTier("SILVER");
                customerRepository.save(targetCustomer);
                customerMutated = true;
            }

            // D. Persist primary Sale entity
            Sale saved = saleRepository.save(sale);
            return ResponseEntity.ok(ApiResponse.success("Checkout processed successfully", saved));

        } catch (DuplicateKeyException dke) {
            // Concurrent duplicate idempotency key race condition caught by MongoDB unique partial index
            cleanupFailedCheckout(storeId, successfullyDeducted, persistedTxIds, targetCustomer, customerMutated, originalLoyalty, originalSpent, originalOrders, originalTier, nowIst);
            if (sale.getIdempotencyKey() != null) {
                java.util.Optional<Sale> existing = saleRepository.findByStoreIdAndIdempotencyKey(storeId, sale.getIdempotencyKey());
                if (existing.isPresent()) {
                    Sale existingSale = existing.get();
                    if (existingSale.getIdempotencyFingerprint() != null && !existingSale.getIdempotencyFingerprint().equals(requestFingerprint)) {
                        return ResponseEntity.status(409).body(ApiResponse.error("Idempotency conflict: key '" + sale.getIdempotencyKey() + "' has already been used with different checkout items or customer."));
                    }
                    return ResponseEntity.ok(ApiResponse.success("Checkout processed successfully (idempotent)", existingSale));
                }
            }
            return ResponseEntity.status(409).body(ApiResponse.error("Concurrent checkout request detected for idempotency key: " + sale.getIdempotencyKey()));

        } catch (Exception ex) {
            // Any persistence failure: cleanly compensate deducted stock, delete orphaned ledger rows, and revert customer
            logger.error("Checkout persistence failure for store {}: {}. Initiating compensating recovery.", storeId, ex.getMessage(), ex);
            cleanupFailedCheckout(storeId, successfullyDeducted, persistedTxIds, targetCustomer, customerMutated, originalLoyalty, originalSpent, originalOrders, originalTier, nowIst);
            return ResponseEntity.status(500).body(ApiResponse.error("Checkout persistence failed: " + ex.getMessage()));
        }
    }

    private String computePayloadFingerprint(Sale sale) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            if (sale.getCustomerId() != null) sb.append("cid:").append(sale.getCustomerId().trim()).append(";");
            if (sale.getCustomerPhone() != null) sb.append("phone:").append(sale.getCustomerPhone().trim()).append(";");

            // Sort products by ID to produce deterministic fingerprint regardless of line ordering
            Map<String, Double> sortedQuantities = new TreeMap<>();
            if (sale.getItems() != null) {
                for (Sale.SaleItem item : sale.getItems()) {
                    if (item.getProductId() != null) {
                        String pid = item.getProductId().trim();
                        sortedQuantities.put(pid, sortedQuantities.getOrDefault(pid, 0.0) + item.getQuantity());
                    }
                }
            }
            for (Map.Entry<String, Double> e : sortedQuantities.entrySet()) {
                sb.append(e.getKey()).append("=").append(e.getValue()).append(";");
            }
            byte[] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(sale.hashCode());
        }
    }

    /**
     * Atomically allocates the next sequential receipt number for the given store,
     * starting at 1 when a new store makes its first sale.
     */
    private String generateNextReceiptNumber(String storeId, int year) {
        Query counterQuery = new Query(Criteria.where("_id").is(storeId));
        Update counterUpdate = new Update().inc("seq", 1L);
        Document counterDoc = mongoTemplate.findAndModify(
                counterQuery,
                counterUpdate,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Document.class,
                "receipt_counters"
        );
        long seq = counterDoc != null && counterDoc.get("seq") != null
                ? ((Number) counterDoc.get("seq")).longValue()
                : 1L;
        return String.format("RCP-%d-%05d", year, seq);
    }

    private void cleanupFailedCheckout(String storeId,
                                       Map<String, Double> successfullyDeducted,
                                       List<String> persistedTxIds,
                                       Customer customer,
                                       boolean customerMutated,
                                       Integer origLoyalty,
                                       Double origSpent,
                                       Integer origOrders,
                                       String origTier,
                                       LocalDateTime nowIst) {
        // 1. Compensating stock rollback
        rollbackDeductedStock(storeId, successfullyDeducted, nowIst);

        // 2. Remove orphaned inventory transactions
        if (persistedTxIds != null && !persistedTxIds.isEmpty()) {
            try {
                Query deleteQuery = new Query(Criteria.where("id").in(persistedTxIds).and("storeId").is(storeId));
                mongoTemplate.remove(deleteQuery, InventoryTransaction.class);
            } catch (Exception ex) {
                logger.error("CRITICAL: Failed to clean up orphaned inventory transactions {} for store {}: {}", persistedTxIds, storeId, ex.getMessage());
            }
        }

        // 3. Revert mutated customer state
        if (customerMutated && customer != null && customer.getId() != null) {
            try {
                customer.setLoyaltyPoints(origLoyalty != null ? origLoyalty : 0);
                customer.setTotalSpent(origSpent != null ? origSpent : 0.0);
                customer.setOrdersCount(origOrders != null ? origOrders : 0);
                customer.setTier(origTier != null ? origTier : "BRONZE");
                customerRepository.save(customer);
            } catch (Exception ex) {
                logger.error("CRITICAL: Failed to revert customer {} state for store {}: {}", customer.getId(), storeId, ex.getMessage());
            }
        }
    }

    private void rollbackDeductedStock(String storeId, Map<String, Double> successfullyDeducted, LocalDateTime nowIst) {
        for (Map.Entry<String, Double> entry : successfullyDeducted.entrySet()) {
            try {
                Query rollbackQuery = new Query(Criteria.where("id").is(entry.getKey()).and("storeId").is(storeId));
                Update rollbackUpdate = new Update().inc("currentStock", entry.getValue()).set("updatedAt", nowIst);
                var updateResult = mongoTemplate.updateFirst(rollbackQuery, rollbackUpdate, Product.class);
                if (updateResult.getModifiedCount() == 0) {
                    logger.error("CRITICAL: Stock rollback failed to modify product {} in store {}. Quantity to restore: {}", entry.getKey(), storeId, entry.getValue());
                }
            } catch (Exception e) {
                logger.error("CRITICAL: Exception occurred while rolling back stock for product {} in store {}: {}", entry.getKey(), storeId, e.getMessage(), e);
            }
        }
    }

    @GetMapping("/sales")
    public ResponseEntity<List<Sale>> getSalesHistory(@AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return ResponseEntity.ok(saleRepository.findByStoreIdOrderByCreatedAtDesc(storeId));
    }

    @GetMapping("/sales/{id}")
    public ResponseEntity<Sale> getSaleById(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return saleRepository.findByStoreIdAndId(storeId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sales/receipt/{receiptNumber}")
    public ResponseEntity<Sale> getSaleByReceiptNumber(
            @PathVariable String receiptNumber,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return saleRepository.findByStoreIdAndReceiptNumber(storeId, receiptNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/returns")
    public ResponseEntity<ApiResponse<ReturnRecord>> processReturn(
            @RequestBody ReturnRecord returnRecord,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        String username = tenantContext.requireUsername(userDetails);

        if (returnRecord.getTotalRefund() <= 0) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Refund amount must be greater than zero"));
        }

        // Idempotency check
        String idemKey = returnRecord.getIdempotencyKey() != null && !returnRecord.getIdempotencyKey().trim().isEmpty()
                ? returnRecord.getIdempotencyKey().trim()
                : null;
        if (idemKey != null) {
            ReturnRecord existing = returnRepository.findByStoreIdAndIdempotencyKey(storeId, idemKey).orElse(null);
            if (existing != null) {
                return ResponseEntity.ok(ApiResponse.success("Return already recorded (idempotent)", existing));
            }
        }

        // Validate item-level quantities & store ownership
        if (returnRecord.getItems() != null && !returnRecord.getItems().isEmpty()) {
            double itemRefundSum = 0.0;
            for (ReturnRecord.ReturnItem item : returnRecord.getItems()) {
                if (item.getQuantity() <= 0 || item.getRefundAmount() < 0) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("Return item quantity must be positive and refundAmount non-negative"));
                }
                if (item.getProductId() != null && !item.getProductId().trim().isEmpty()) {
                    if (productRepository.findByStoreIdAndId(storeId, item.getProductId()).isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(ApiResponse.error("Returned product does not belong to your store: " + item.getProductId()));
                    }
                }
                itemRefundSum += item.getRefundAmount();
            }
            if (itemRefundSum > 0 && Math.abs(itemRefundSum - returnRecord.getTotalRefund()) > 0.01) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Item refund amounts do not match totalRefund"));
            }
        }

        // Atomic over-refund & item-quantity reservation on Sale (standalone MongoDB single-document atomicity)
        boolean reservedOnSale = false;
        String reservationId = null;
        String saleId = returnRecord.getSaleId() != null && !returnRecord.getSaleId().trim().isEmpty()
                ? returnRecord.getSaleId().trim()
                : null;
        Map<String, Double> qtyBySaleItemId = new HashMap<>();

        if (saleId != null) {
            Sale originalSale = saleRepository.findByStoreIdAndId(storeId, saleId).orElse(null);
            if (originalSale == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Referenced sale does not belong to your store"));
            }

            // Validate & associate every ReturnItem with an exact Sale.SaleItem
            Map<String, Sale.SaleItem> saleItemsById = new HashMap<>();
            if (originalSale.getItems() != null) {
                for (Sale.SaleItem si : originalSale.getItems()) {
                    if (si.getSaleItemId() != null) {
                        saleItemsById.put(si.getSaleItemId(), si);
                    }
                }
            }
            if (returnRecord.getItems() != null && !returnRecord.getItems().isEmpty()) {
                for (ReturnRecord.ReturnItem rItem : returnRecord.getItems()) {
                    Sale.SaleItem matched = null;
                    if (rItem.getSaleItemId() != null && !rItem.getSaleItemId().trim().isEmpty()) {
                        matched = saleItemsById.get(rItem.getSaleItemId().trim());
                        if (matched == null) {
                            return ResponseEntity.badRequest()
                                    .body(ApiResponse.error("Unknown saleItemId for this sale: " + rItem.getSaleItemId()));
                        }
                    } else if (rItem.getProductId() != null && !rItem.getProductId().trim().isEmpty()) {
                        String pid = rItem.getProductId().trim();
                        List<Sale.SaleItem> matches = originalSale.getItems() == null ? List.of() :
                                originalSale.getItems().stream().filter(si -> pid.equals(si.getProductId())).toList();
                        if (matches.size() == 1) {
                            matched = matches.get(0);
                        } else {
                            return ResponseEntity.badRequest()
                                    .body(ApiResponse.error("Return item must specify a valid saleItemId"));
                        }
                    } else {
                        return ResponseEntity.badRequest()
                                .body(ApiResponse.error("Return item must specify saleItemId"));
                    }

                    if (rItem.getProductId() != null && !rItem.getProductId().trim().isEmpty()
                            && !rItem.getProductId().trim().equals(matched.getProductId())) {
                        return ResponseEntity.badRequest()
                                .body(ApiResponse.error("Returned productId does not match original sale item"));
                    }
                    rItem.setSaleItemId(matched.getSaleItemId());
                    rItem.setProductId(matched.getProductId());
                    qtyBySaleItemId.merge(matched.getSaleItemId(), rItem.getQuantity(), Double::sum);
                }
            }

            double legacyCompleted = returnRepository.findByStoreIdAndSaleId(storeId, saleId).stream()
                    .filter(r -> "COMPLETED".equalsIgnoreCase(r.getStatus()))
                    .mapToDouble(ReturnRecord::getTotalRefund)
                    .sum();
            reservationId = UUID.randomUUID().toString();
            long nowEpochMs = System.currentTimeMillis();

            List<Document> exprAnd = new ArrayList<>();
            exprAnd.add(new Document("$lte", List.of(
                    new Document("$add", List.of(
                            new Document("$ifNull", List.of("$refundedTotal", 0.0)),
                            new Document("$ifNull", List.of("$pendingRefundTotal", 0.0)),
                            returnRecord.getTotalRefund()
                    )),
                    new Document("$add", List.of("$grandTotal", 0.001))
            )));
            for (Map.Entry<String, Double> entry : qtyBySaleItemId.entrySet()) {
                String sid = entry.getKey();
                double reqQty = entry.getValue();
                double soldQty = saleItemsById.get(sid).getQuantity();
                exprAnd.add(new Document("$lte", List.of(
                        new Document("$add", List.of(
                                new Document("$ifNull", List.of("$itemReturnedQty." + sid, 0.0)),
                                new Document("$ifNull", List.of("$itemPendingReturnQty." + sid, 0.0)),
                                reqQty
                        )),
                        soldQty + 0.001
                )));
            }

            Document exprDoc = new Document("$and", exprAnd);
            Document queryDoc = new Document("_id", saleId)
                    .append("storeId", storeId)
                    .append("activeReservations." + reservationId, new Document("$exists", false))
                    .append("$expr", exprDoc);
            Query reserveQuery = new BasicQuery(queryDoc);
            Sale.ReservationMetadata meta = new Sale.ReservationMetadata("RESERVED", nowEpochMs, qtyBySaleItemId);
            Update reserveUpdate = new Update()
                    .inc("pendingRefundTotal", returnRecord.getTotalRefund())
                    .set("activeReservations." + reservationId, returnRecord.getTotalRefund())
                    .set("reservationMetadata." + reservationId, meta);
            for (Map.Entry<String, Double> entry : qtyBySaleItemId.entrySet()) {
                reserveUpdate.inc("itemPendingReturnQty." + entry.getKey(), entry.getValue());
            }

            Sale reserved = mongoTemplate.findAndModify(
                    reserveQuery, reserveUpdate, FindAndModifyOptions.options().returnNew(true), Sale.class);
            if (reserved == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Total refund or item return quantity exceeds eligible remaining limit"));
            }
            if (legacyCompleted > reserved.getRefundedTotal()
                    && legacyCompleted + reserved.getPendingRefundTotal() > reserved.getGrandTotal() + 0.001) {
                releaseReservation(storeId, saleId, reservationId, returnRecord.getTotalRefund());
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Total refund exceeds original sale grand total"));
            }
            reservedOnSale = true;
            if (returnRecord.getReceiptNumber() == null || returnRecord.getReceiptNumber().isBlank()) {
                returnRecord.setReceiptNumber(reserved.getReceiptNumber());
            }
        }

        Instant nowInstant = Instant.now();
        LocalDateTime nowIst = LocalDateTime.ofInstant(nowInstant, IST);
        returnRecord.setStoreId(storeId);
        returnRecord.setProcessedBy(username);
        returnRecord.setIdempotencyKey(idemKey);
        returnRecord.setReservationId(reservationId);
        returnRecord.setSaleSettled(false);
        returnRecord.setReturnNumber("RET-" + nowIst.getYear() + "-" + (int)(Math.random() * 90000 + 10000));
        returnRecord.setCreatedAt(nowIst);
        // Separate return acceptance from refund payment settlement
        returnRecord.setStatus("PENDING");
        returnRecord.setRestockProcessed(false);

        ReturnRecord saved;
        try {
            saved = returnRepository.save(returnRecord);
        } catch (RuntimeException ex) {
            // Handle ambiguous save outcomes: check if ReturnRecord actually persisted before releasing
            ReturnRecord persistedByRes = reservationId != null
                    ? returnRepository.findByStoreIdAndReservationId(storeId, reservationId).orElse(null)
                    : null;
            if (persistedByRes != null) {
                saved = persistedByRes;
            } else {
                if (reservedOnSale) {
                    try {
                        releaseReservation(storeId, saleId, reservationId, returnRecord.getTotalRefund());
                    } catch (RuntimeException compEx) {
                        ex.addSuppressed(compEx);
                    }
                }
                if (ex instanceof DuplicateKeyException && idemKey != null) {
                    ReturnRecord existing = returnRepository.findByStoreIdAndIdempotencyKey(storeId, idemKey).orElse(null);
                    if (existing != null) {
                        return ResponseEntity.ok(ApiResponse.success("Return already recorded (idempotent)", existing));
                    }
                }
                throw ex;
            }
        }

        // Transition reservation state RESERVED -> LINKED atomically before restocking
        if (reservedOnSale && reservationId != null) {
            Query linkQuery = new Query(Criteria.where("_id").is(saleId)
                    .and("storeId").is(storeId)
                    .and("reservationMetadata." + reservationId + ".status").is("RESERVED"));
            Update linkUpdate = new Update().set("reservationMetadata." + reservationId + ".status", "LINKED");
            Sale linkedSale = mongoTemplate.findAndModify(linkQuery, linkUpdate, Sale.class);
            if (linkedSale == null) {
                // Reconciler already claimed this reservation as RECOVERY_CLAIMED; abort cleanly without restocking
                returnRepository.deleteById(saved.getId());
                return ResponseEntity.status(409)
                        .body(ApiResponse.error("Return reservation was claimed for recovery; please retry"));
            }
        }

        // Restock items exactly once upon initial ReturnRecord creation
        if (saved.getItems() != null && !saved.isRestockProcessed()) {
            for (ReturnRecord.ReturnItem item : saved.getItems()) {
                if (item.isRestocked() && item.getProductId() != null && !item.getProductId().trim().isEmpty()) {
                    Product product = productRepository.findByStoreIdAndId(storeId, item.getProductId()).orElse(null);
                    if (product != null) {
                        double newStock = product.getCurrentStock() + item.getQuantity();
                        product.setCurrentStock(newStock);
                        product.setUpdatedAt(nowIst);
                        productRepository.save(product);

                        InventoryTransaction tx = new InventoryTransaction(
                                product.getId(),
                                product.getName(),
                                "CUSTOMER_RETURN",
                                item.getQuantity(),
                                newStock,
                                "RETURN",
                                saved.getReturnNumber(),
                                username,
                                "Return: " + item.getReason()
                        );
                        tx.setStoreId(storeId);
                        transactionRepository.save(tx);
                    }
                }
            }
            saved.setRestockProcessed(true);
            saved = returnRepository.save(saved);
        }

        return ResponseEntity.ok(ApiResponse.success("Return accepted and awaiting refund confirmation", saved));
    }

    @PostMapping("/returns/{id}/confirm")
    public ResponseEntity<ApiResponse<ReturnRecord>> confirmReturnRefund(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        String username = tenantContext.requireUsername(userDetails);
        LocalDateTime nowIst = LocalDateTime.now(IST);

        Query q = new Query(Criteria.where("_id").is(id).and("storeId").is(storeId).and("status").is("PENDING"));
        Update u = new Update()
                .set("status", "COMPLETED")
                .set("saleSettled", false)
                .set("confirmedBy", username)
                .set("confirmedAt", nowIst);
        ReturnRecord updated = mongoTemplate.findAndModify(
                q, u, FindAndModifyOptions.options().returnNew(true), ReturnRecord.class);

        if (updated == null) {
            ReturnRecord existing = returnRepository.findByStoreIdAndId(storeId, id).orElse(null);
            if (existing == null) {
                return ResponseEntity.notFound().build();
            }
            if ("COMPLETED".equalsIgnoreCase(existing.getStatus())) {
                if (!existing.isSaleSettled()) {
                    existing = applySettlementToSale(storeId, existing);
                }
                return ResponseEntity.ok(ApiResponse.success("Refund already confirmed", existing));
            }
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Cannot confirm return in status: " + existing.getStatus()));
        }

        updated = applySettlementToSale(storeId, updated);
        return ResponseEntity.ok(ApiResponse.success("Refund confirmed as completed", updated));
    }

    public ReturnRecord applySettlementToSale(String storeId, ReturnRecord ret) {
        if (ret.getSaleId() != null && !ret.getSaleId().isBlank()) {
            // Release reservation idempotently (only if activeReservations.{reservationId} still exists)
            if (ret.getReservationId() != null && !ret.getReservationId().isBlank()) {
                releaseReservation(storeId, ret.getSaleId(), ret.getReservationId(), ret.getTotalRefund());
            }

            // Apply settlement idempotently using appliedSettlementIds guard
            Query saleQuery = new Query(Criteria.where("_id").is(ret.getSaleId())
                    .and("storeId").is(storeId)
                    .and("appliedSettlementIds").ne(ret.getId()));
            Update saleUpdate = new Update()
                    .inc("refundedTotal", ret.getTotalRefund())
                    .addToSet("appliedSettlementIds", ret.getId());
            if (ret.getItems() != null) {
                Map<String, Double> settledQtyByItem = new HashMap<>();
                for (ReturnRecord.ReturnItem ri : ret.getItems()) {
                    if (ri.getSaleItemId() != null && !ri.getSaleItemId().isBlank() && ri.getQuantity() > 0) {
                        settledQtyByItem.merge(ri.getSaleItemId(), ri.getQuantity(), Double::sum);
                    }
                }
                for (Map.Entry<String, Double> e : settledQtyByItem.entrySet()) {
                    saleUpdate.inc("itemReturnedQty." + e.getKey(), e.getValue());
                }
            }
            Sale updatedSale = mongoTemplate.findAndModify(
                    saleQuery, saleUpdate, FindAndModifyOptions.options().returnNew(true), Sale.class);
            if (updatedSale == null) {
                updatedSale = saleRepository.findByStoreIdAndId(storeId, ret.getSaleId()).orElse(null);
            }
            if (updatedSale != null) {
                String nextStatus = updatedSale.getRefundedTotal() >= updatedSale.getGrandTotal() - 0.01
                        ? "REFUNDED"
                        : "PARTIALLY_RETURNED";
                mongoTemplate.updateFirst(
                        new Query(Criteria.where("_id").is(updatedSale.getId()).and("storeId").is(storeId)),
                        new Update().set("status", nextStatus),
                        Sale.class);
            }
        }
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(ret.getId()).and("storeId").is(storeId)),
                new Update().set("saleSettled", true),
                ReturnRecord.class);
        ret.setSaleSettled(true);
        return ret;
    }

    @PostMapping("/returns/{id}/fail")
    public ResponseEntity<ApiResponse<ReturnRecord>> failReturnRefund(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return transitionPendingReturn(id, "FAILED", userDetails);
    }

    @PostMapping("/returns/{id}/reject")
    public ResponseEntity<ApiResponse<ReturnRecord>> rejectReturnRefund(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return transitionPendingReturn(id, "REJECTED", userDetails);
    }

    @PostMapping("/returns/reconcile")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> reconcileReturns(
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Reconciliation completed", reconcileStoreReturns(storeId)));
    }

    public void setReservationGracePeriodMs(long reservationGracePeriodMs) {
        this.reservationGracePeriodMs = Math.max(0L, reservationGracePeriodMs);
    }

    public Map<String, Integer> reconcileStoreReturns(String storeId) {
        int settledCount = 0;
        int releasedOrphanCount = 0;

        // 1. Complete any COMPLETED returns whose Sale settlement was interrupted
        List<ReturnRecord> unsettled = returnRepository.findByStoreIdAndStatusAndSaleSettled(storeId, "COMPLETED", false);
        for (ReturnRecord r : unsettled) {
            applySettlementToSale(storeId, r);
            settledCount++;
        }

        // 2. State-guarded orphan reservation cleanup
        long nowMs = System.currentTimeMillis();
        List<Sale> salesWithReservations = mongoTemplate.find(
                new Query(Criteria.where("storeId").is(storeId).and("pendingRefundTotal").gt(0)),
                Sale.class);
        for (Sale s : salesWithReservations) {
            if (s.getActiveReservations() == null || s.getActiveReservations().isEmpty()) continue;
            for (Map.Entry<String, Double> entry : new HashMap<>(s.getActiveReservations()).entrySet()) {
                String resId = entry.getKey();
                double amt = entry.getValue() != null ? entry.getValue() : 0.0;
                Sale.ReservationMetadata meta = s.getReservationMetadata() != null
                        ? s.getReservationMetadata().get(resId)
                        : null;
                String resStatus = meta != null ? meta.getStatus() : null;

                if ("LINKED".equals(resStatus)) {
                    ReturnRecord linked = returnRepository.findByStoreIdAndReservationId(storeId, resId).orElse(null);
                    if (linked != null && ("PENDING".equalsIgnoreCase(linked.getStatus())
                            || ("COMPLETED".equalsIgnoreCase(linked.getStatus()) && !linked.isSaleSettled()))) {
                        continue;
                    }
                    // Linked return is FAILED/REJECTED or missing: release safely
                    if (releaseReservation(storeId, s.getId(), resId, amt)) {
                        releasedOrphanCount++;
                    }
                    continue;
                }

                if ("RECOVERY_CLAIMED".equals(resStatus)) {
                    // Another worker or interrupted recovery claimed it; verify no ReturnRecord exists before releasing
                    ReturnRecord linked = returnRepository.findByStoreIdAndReservationId(storeId, resId).orElse(null);
                    if (linked == null) {
                        if (releaseReservation(storeId, s.getId(), resId, amt)) {
                            releasedOrphanCount++;
                        }
                    } else if ("PENDING".equalsIgnoreCase(linked.getStatus())) {
                        mongoTemplate.updateFirst(
                                new Query(Criteria.where("_id").is(s.getId()).and("storeId").is(storeId)
                                        .and("reservationMetadata." + resId + ".status").is("RECOVERY_CLAIMED")),
                                new Update().set("reservationMetadata." + resId + ".status", "LINKED"),
                                Sale.class);
                    }
                    continue;
                }

                // Status is RESERVED (or legacy null metadata)
                long createdAtMs = meta != null ? meta.getCreatedAtEpochMs() : 0L;
                if (nowMs - createdAtMs < reservationGracePeriodMs) {
                    continue; // Active in-flight window; do not disturb
                }

                ReturnRecord linked = returnRepository.findByStoreIdAndReservationId(storeId, resId).orElse(null);
                if (linked != null) {
                    if ("PENDING".equalsIgnoreCase(linked.getStatus())) {
                        mongoTemplate.updateFirst(
                                new Query(Criteria.where("_id").is(s.getId()).and("storeId").is(storeId)
                                        .and("reservationMetadata." + resId + ".status").is("RESERVED")),
                                new Update().set("reservationMetadata." + resId + ".status", "LINKED"),
                                Sale.class);
                    } else if ("COMPLETED".equalsIgnoreCase(linked.getStatus()) && !linked.isSaleSettled()) {
                        applySettlementToSale(storeId, linked);
                        settledCount++;
                    } else {
                        if (releaseReservation(storeId, s.getId(), resId, amt)) {
                            releasedOrphanCount++;
                        }
                    }
                    continue;
                }

                // Atomically claim RESERVED -> RECOVERY_CLAIMED so concurrent reconcilers or processReturn cannot collide
                Criteria claimCriteria = Criteria.where("_id").is(s.getId())
                        .and("storeId").is(storeId)
                        .and("activeReservations." + resId).exists(true);
                if (meta != null) {
                    claimCriteria = claimCriteria.and("reservationMetadata." + resId + ".status").is("RESERVED");
                } else {
                    claimCriteria = claimCriteria.and("reservationMetadata." + resId).exists(false);
                }
                Sale claimed = mongoTemplate.findAndModify(
                        new Query(claimCriteria),
                        new Update().set("reservationMetadata." + resId + ".status", "RECOVERY_CLAIMED"),
                        Sale.class);
                if (claimed == null) {
                    continue; // Already claimed by another thread or linked by processReturn
                }

                // Double-check ReturnRecord after atomic claim
                ReturnRecord afterClaim = returnRepository.findByStoreIdAndReservationId(storeId, resId).orElse(null);
                if (afterClaim != null) {
                    mongoTemplate.updateFirst(
                            new Query(Criteria.where("_id").is(s.getId()).and("storeId").is(storeId)
                                    .and("reservationMetadata." + resId + ".status").is("RECOVERY_CLAIMED")),
                            new Update().set("reservationMetadata." + resId + ".status", "LINKED"),
                            Sale.class);
                    continue;
                }

                if (releaseReservation(storeId, s.getId(), resId, amt)) {
                    releasedOrphanCount++;
                }
            }
        }

        return Map.of("settledReturns", settledCount, "releasedOrphanReservations", releasedOrphanCount);
    }

    private ResponseEntity<ApiResponse<ReturnRecord>> transitionPendingReturn(
            String id, String targetStatus, UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        Query q = new Query(Criteria.where("_id").is(id).and("storeId").is(storeId).and("status").is("PENDING"));
        Update u = new Update().set("status", targetStatus);
        ReturnRecord updated = mongoTemplate.findAndModify(
                q, u, FindAndModifyOptions.options().returnNew(true), ReturnRecord.class);
        if (updated == null) {
            return returnRepository.findByStoreIdAndId(storeId, id).isEmpty()
                    ? ResponseEntity.notFound().build()
                    : ResponseEntity.badRequest().body(ApiResponse.error("Return is not in PENDING status"));
        }
        if (updated.getSaleId() != null && !updated.getSaleId().isBlank()) {
            if (updated.getReservationId() != null && !updated.getReservationId().isBlank()) {
                releaseReservation(storeId, updated.getSaleId(), updated.getReservationId(), updated.getTotalRefund());
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Return marked as " + targetStatus, updated));
    }

    public boolean releaseReservation(String storeId, String saleId, String reservationId, double fallbackAmount) {
        if (saleId == null || saleId.isBlank() || reservationId == null || reservationId.isBlank()) {
            return false;
        }
        Sale sale = saleRepository.findByStoreIdAndId(storeId, saleId).orElse(null);
        if (sale == null || sale.getActiveReservations() == null || !sale.getActiveReservations().containsKey(reservationId)) {
            return false;
        }
        Double reservedBoxed = sale.getActiveReservations().get(reservationId);
        double releaseAmt = (reservedBoxed != null && reservedBoxed > 0) ? reservedBoxed : fallbackAmount;
        Query q = new Query(Criteria.where("_id").is(saleId)
                .and("storeId").is(storeId)
                .and("activeReservations." + reservationId).exists(true));
        Update u = new Update()
                .inc("pendingRefundTotal", -releaseAmt)
                .unset("activeReservations." + reservationId)
                .unset("reservationMetadata." + reservationId);
        Sale.ReservationMetadata meta = sale.getReservationMetadata() != null
                ? sale.getReservationMetadata().get(reservationId)
                : null;
        if (meta != null && meta.getItemQuantities() != null) {
            for (Map.Entry<String, Double> iq : meta.getItemQuantities().entrySet()) {
                if (iq.getValue() != null && iq.getValue() > 0) {
                    u.inc("itemPendingReturnQty." + iq.getKey(), -iq.getValue());
                }
            }
        }
        Sale modified = mongoTemplate.findAndModify(q, u, Sale.class);
        return modified != null;
    }

    @GetMapping("/returns")
    public ResponseEntity<List<ReturnRecord>> getReturns(@AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return ResponseEntity.ok(returnRepository.findByStoreIdOrderByCreatedAtDesc(storeId));
    }
}
