package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.InventoryBatch;
import com.grocery.model.InventoryTransaction;
import com.grocery.model.Notification;
import com.grocery.model.Product;
import com.grocery.repository.InventoryBatchRepository;
import com.grocery.repository.InventoryTransactionRepository;
import com.grocery.repository.NotificationRepository;
import com.grocery.repository.ProductRepository;
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
@RequestMapping("/api/v1/inventory")
@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
public class InventoryController {

    private final ProductRepository productRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final NotificationRepository notificationRepository;
    private final TenantContext tenantContext;

    public InventoryController(ProductRepository productRepository,
                               InventoryBatchRepository batchRepository,
                               InventoryTransactionRepository transactionRepository,
                               NotificationRepository notificationRepository,
                               TenantContext tenantContext) {
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.notificationRepository = notificationRepository;
        this.tenantContext = tenantContext;
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

        String productId = (String) payload.get("productId");
        String adjustmentType = (String) payload.getOrDefault("type", "ADJUSTMENT");
        double quantity = Double.parseDouble(payload.get("quantity").toString());
        String reason = (String) payload.getOrDefault("notes", "Physical stock adjustment");

        Product product = productRepository.findByStoreIdAndId(storeId, productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Product not found in your store"));
        }

        double oldStock = product.getCurrentStock();
        double newStock = Math.max(0, oldStock + quantity);
        product.setCurrentStock(newStock);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        InventoryTransaction tx = new InventoryTransaction(
                product.getId(),
                product.getName(),
                adjustmentType,
                quantity,
                newStock,
                "ADJUSTMENT",
                "ADJ-" + System.currentTimeMillis() % 100000,
                username,
                reason
        );
        tx.setStoreId(storeId);
        InventoryTransaction savedTx = transactionRepository.save(tx);

        if (newStock <= product.getMinStockLevel()) {
            Notification notif = new Notification(
                    "Low Stock Alert: " + product.getName(),
                    "Stock adjusted to " + newStock + " " + product.getUnit() + " (Min: " + product.getMinStockLevel() + ")",
                    "LOW_STOCK",
                    "HIGH",
                    "inventory.html"
            );
            notif.setStoreId(storeId);
            notificationRepository.save(notif);
        }

        return ResponseEntity.ok(ApiResponse.success("Inventory adjusted successfully", savedTx));
    }
}
