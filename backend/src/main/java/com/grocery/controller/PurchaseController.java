package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.*;
import com.grocery.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseController {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;

    public PurchaseController(PurchaseOrderRepository purchaseOrderRepository,
                              ProductRepository productRepository,
                              InventoryBatchRepository batchRepository,
                              InventoryTransactionRepository transactionRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
    }

    @GetMapping
    public ResponseEntity<List<PurchaseOrder>> getPurchaseOrders(@RequestParam(required = false) String status) {
        if (status != null && !status.isEmpty()) {
            return ResponseEntity.ok(purchaseOrderRepository.findByStatusOrderByCreatedAtDesc(status));
        }
        return ResponseEntity.ok(purchaseOrderRepository.findAllByOrderByCreatedAtDesc());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseOrder>> createPurchaseOrder(@RequestBody PurchaseOrder po) {
        if (po.getOrderNumber() == null || po.getOrderNumber().isEmpty()) {
            po.setOrderNumber("PO-" + LocalDate.now().getYear() + "-" + (purchaseOrderRepository.count() + 101));
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
    public ResponseEntity<ApiResponse<PurchaseOrder>> submitPurchaseOrder(@PathVariable String id) {
        return purchaseOrderRepository.findById(id).map(po -> {
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
        String username = userDetails != null ? userDetails.getUsername() : "Staff";
        PurchaseOrder po = purchaseOrderRepository.findById(id).orElse(null);
        if (po == null) {
            return ResponseEntity.notFound().build();
        }

        if ("RECEIVED".equals(po.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Purchase order already received and stocked"));
        }

        po.setStatus("RECEIVED");
        purchaseOrderRepository.save(po);

        // For each item in PO, inward inventory batch and increase product stock
        if (po.getItems() != null) {
            for (PurchaseOrder.PurchaseOrderItem item : po.getItems()) {
                Product product = productRepository.findById(item.getProductId()).orElse(null);
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
                    transactionRepository.save(tx);
                }
            }
        }

        return ResponseEntity.ok(ApiResponse.success("Goods received and stock inwarded successfully", po));
    }
}
