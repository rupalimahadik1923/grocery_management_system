package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.*;
import com.grocery.repository.*;
import com.grocery.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/purchases")
@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
public class PurchaseController {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final TenantContext tenantContext;

    public PurchaseController(PurchaseOrderRepository purchaseOrderRepository,
                              ProductRepository productRepository,
                              SupplierRepository supplierRepository,
                              InventoryBatchRepository batchRepository,
                              InventoryTransactionRepository transactionRepository,
                              TenantContext tenantContext) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.tenantContext = tenantContext;
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

        // Validate supplier ownership if supplierId is specified
        if (po.getSupplierId() != null && !po.getSupplierId().trim().isEmpty()) {
            if (supplierRepository.findByStoreIdAndId(storeId, po.getSupplierId().trim()).isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Supplier does not belong to your store"));
            }
        }

        // Validate all referenced products belong to the same store
        if (po.getItems() != null) {
            for (PurchaseOrder.PurchaseOrderItem item : po.getItems()) {
                if (item.getProductId() != null && !item.getProductId().trim().isEmpty()) {
                    if (productRepository.findByStoreIdAndId(storeId, item.getProductId().trim()).isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(ApiResponse.error("Product does not belong to your store: " + item.getProductId()));
                    }
                }
            }
        }

        po.setStoreId(storeId);
        if (po.getOrderNumber() == null || po.getOrderNumber().isEmpty()) {
            po.setOrderNumber("PO-" + LocalDate.now().getYear() + "-" + (purchaseOrderRepository.countByStoreId(storeId) + 101));
        }
        po.setStatus("DRAFT");
        po.setCreatedAt(LocalDateTime.now());
        if (po.getItems() != null) {
            double total = po.getItems().stream().mapToDouble(PurchaseOrder.PurchaseOrderItem::getTotalCost).sum();
            po.setTotalAmount(total);
        }
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return ResponseEntity.ok(ApiResponse.success("Purchase order created", saved));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<PurchaseOrder>> submitPurchaseOrder(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return purchaseOrderRepository.findByStoreIdAndId(storeId, id).map(po -> {
            po.setStatus("ORDERED");
            PurchaseOrder saved = purchaseOrderRepository.save(po);
            return ResponseEntity.ok(ApiResponse.success("Purchase order marked as ORDERED with supplier", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/receive")
    public ResponseEntity<ApiResponse<PurchaseOrder>> receiveGoods(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> payload,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        String username = tenantContext.requireUsername(userDetails);
        PurchaseOrder po = purchaseOrderRepository.findByStoreIdAndId(storeId, id).orElse(null);
        if (po == null) {
            return ResponseEntity.notFound().build();
        }

        if ("RECEIVED".equals(po.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Purchase order already received and stocked"));
        }

        po.setStatus("RECEIVED");
        purchaseOrderRepository.save(po);

        if (po.getItems() != null) {
            for (PurchaseOrder.PurchaseOrderItem item : po.getItems()) {
                Product product = productRepository.findByStoreIdAndId(storeId, item.getProductId()).orElse(null);
                if (product != null) {
                    double newStock = product.getCurrentStock() + item.getOrderQty();
                    product.setCurrentStock(newStock);
                    product.setUpdatedAt(LocalDateTime.now());
                    productRepository.save(product);

                    String batchNum = "BATCH-" + LocalDate.now().getYear() + "-" + (int)(Math.random() * 9000 + 1000);
                    InventoryBatch batch = new InventoryBatch(
                            product.getId(),
                            product.getName(),
                            batchNum,
                            po.getSupplierId(),
                            po.getSupplierName(),
                            LocalDate.now(),
                            LocalDate.now().plusMonths(6),
                            item.getOrderQty(),
                            item.getOrderQty(),
                            item.getUnitCost()
                    );
                    batch.setStoreId(storeId);
                    batchRepository.save(batch);

                    InventoryTransaction tx = new InventoryTransaction(
                            product.getId(),
                            product.getName(),
                            "PURCHASE_RECEIPT",
                            item.getOrderQty(),
                            newStock,
                            "PURCHASE_ORDER",
                            po.getOrderNumber(),
                            username,
                            "Goods received from PO: " + po.getOrderNumber()
                    );
                    tx.setStoreId(storeId);
                    transactionRepository.save(tx);
                }
            }
        }

        return ResponseEntity.ok(ApiResponse.success("Goods received and stock inwarded successfully", po));
    }
}
