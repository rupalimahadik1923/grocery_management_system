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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final ProductRepository productRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final NotificationRepository notificationRepository;

    public InventoryController(ProductRepository productRepository,
                               InventoryBatchRepository batchRepository,
                               InventoryTransactionRepository transactionRepository,
                               NotificationRepository notificationRepository) {
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public ResponseEntity<List<Product>> getInventoryOverview() {
        return ResponseEntity.ok(productRepository.findAll());
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<Product>> getLowStockProducts() {
        return ResponseEntity.ok(productRepository.findLowStockProducts());
    }

    @GetMapping("/expiring")
    public ResponseEntity<List<InventoryBatch>> getExpiringBatches(@RequestParam(defaultValue = "30") int days) {
        LocalDate now = LocalDate.now();
        LocalDate future = now.plusDays(days);
        return ResponseEntity.ok(batchRepository.findByExpiryDateBetweenAndStatus(now, future, "ACTIVE"));
    }

    @GetMapping("/batches")
    public ResponseEntity<List<InventoryBatch>> getAllBatches() {
        return ResponseEntity.ok(batchRepository.findAll());
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<InventoryTransaction>> getAuditTransactions() {
        return ResponseEntity.ok(transactionRepository.findTop50ByOrderByCreatedAtDesc());
    }

    @PostMapping("/adjustments")
    public ResponseEntity<ApiResponse<InventoryTransaction>> recordAdjustment(
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal UserDetails userDetails) {
        String productId = (String) payload.get("productId");
        String adjustmentType = (String) payload.getOrDefault("type", "ADJUSTMENT");
        double quantity = Double.parseDouble(payload.get("quantity").toString());
        String reason = (String) payload.getOrDefault("notes", "Physical stock adjustment");
        String username = userDetails != null ? userDetails.getUsername() : "Staff";

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Product not found"));
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
        InventoryTransaction savedTx = transactionRepository.save(tx);

        if (newStock <= product.getMinStockLevel()) {
            notificationRepository.save(new Notification(
                    "Low Stock Alert: " + product.getName(),
                    "Current stock dropped to " + newStock + " " + product.getUnit() + " (threshold: " + product.getMinStockLevel() + ")",
                    "LOW_STOCK",
                    "HIGH",
                    "inventory.html"
            ));
        }

        return ResponseEntity.ok(ApiResponse.success("Stock adjustment successfully recorded", savedTx));
    }
}
