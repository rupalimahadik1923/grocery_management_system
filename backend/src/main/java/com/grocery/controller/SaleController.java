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

@RestController
@RequestMapping("/api/v1")
public class SaleController {

    private final SaleRepository saleRepository;
    private final ReturnRepository returnRepository;
    private final ProductRepository productRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final NotificationRepository notificationRepository;

    public SaleController(SaleRepository saleRepository,
                          ReturnRepository returnRepository,
                          ProductRepository productRepository,
                          InventoryBatchRepository batchRepository,
                          InventoryTransactionRepository transactionRepository,
                          CustomerRepository customerRepository,
                          NotificationRepository notificationRepository) {
        this.saleRepository = saleRepository;
        this.returnRepository = returnRepository;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
        this.notificationRepository = notificationRepository;
    }

    @PostMapping("/sales")
    public ResponseEntity<ApiResponse<Sale>> processCheckout(
            @RequestBody Sale sale,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "Staff";
        sale.setCashierName(username);
        sale.setReceiptNumber("RCP-" + LocalDate.now().getYear() + "-" + (int)(Math.random() * 90000 + 10000));
        sale.setCreatedAt(LocalDateTime.now());
        sale.setStatus("COMPLETED");

        // Deduct inventory for each item
        if (sale.getItems() != null) {
            for (Sale.SaleItem item : sale.getItems()) {
                Product product = productRepository.findById(item.getProductId()).orElse(null);
                if (product != null) {
                    double newStock = Math.max(0, product.getCurrentStock() - item.getQuantity());
                    product.setCurrentStock(newStock);
                    product.setUpdatedAt(LocalDateTime.now());
                    productRepository.save(product);

                    // Record audit ledger
                    InventoryTransaction tx = new InventoryTransaction(
                            product.getId(),
                            product.getName(),
                            "SALE",
                            -item.getQuantity(),
                            newStock,
                            "POS_SALE",
                            sale.getReceiptNumber(),
                            username,
                            "POS Checkout item: " + product.getName()
                    );
                    transactionRepository.save(tx);

                    // Low stock alert check
                    if (newStock <= product.getMinStockLevel()) {
                        notificationRepository.save(new Notification(
                                "Low Stock Alert: " + product.getName(),
                                "Stock decreased to " + newStock + " " + product.getUnit() + " (Min: " + product.getMinStockLevel() + ")",
                                "LOW_STOCK",
                                "HIGH",
                                "inventory.html"
                        ));
                    }
                }
            }
        }

        // Update customer loyalty points if phone present
        if (sale.getCustomerPhone() != null && !sale.getCustomerPhone().trim().isEmpty()) {
            customerRepository.findByPhone(sale.getCustomerPhone().trim()).ifPresent(c -> {
                int earnedPoints = (int)(sale.getGrandTotal() / 100);
                c.setLoyaltyPoints(c.getLoyaltyPoints() + earnedPoints);
                c.setTotalSpent(c.getTotalSpent() + sale.getGrandTotal());
                c.setOrdersCount(c.getOrdersCount() + 1);
                if (c.getTotalSpent() > 20000) c.setTier("GOLD");
                else if (c.getTotalSpent() > 5000) c.setTier("SILVER");
                customerRepository.save(c);
            });
        }

        Sale saved = saleRepository.save(sale);
        return ResponseEntity.ok(ApiResponse.success("Checkout processed successfully", saved));
    }

    @GetMapping("/sales")
    public ResponseEntity<List<Sale>> getSalesHistory() {
        return ResponseEntity.ok(saleRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/sales/{id}")
    public ResponseEntity<Sale> getSaleById(@PathVariable String id) {
        return saleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sales/receipt/{receiptNumber}")
    public ResponseEntity<Sale> getSaleByReceiptNumber(@PathVariable String receiptNumber) {
        return saleRepository.findByReceiptNumber(receiptNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/returns")
    public ResponseEntity<ApiResponse<ReturnRecord>> processReturn(
            @RequestBody ReturnRecord returnRecord,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "Staff";
        returnRecord.setProcessedBy(username);
        returnRecord.setReturnNumber("RET-" + LocalDate.now().getYear() + "-" + (int)(Math.random() * 90000 + 10000));
        returnRecord.setCreatedAt(LocalDateTime.now());

        // Restock inventory if requested
        if (returnRecord.getItems() != null) {
            for (ReturnRecord.ReturnItem item : returnRecord.getItems()) {
                if (item.isRestocked()) {
                    Product product = productRepository.findById(item.getProductId()).orElse(null);
                    if (product != null) {
                        double newStock = product.getCurrentStock() + item.getQuantity();
                        product.setCurrentStock(newStock);
                        product.setUpdatedAt(LocalDateTime.now());
                        productRepository.save(product);

                        InventoryTransaction tx = new InventoryTransaction(
                                product.getId(),
                                product.getName(),
                                "CUSTOMER_RETURN",
                                item.getQuantity(),
                                newStock,
                                "RETURN",
                                returnRecord.getReturnNumber(),
                                username,
                                "Return: " + item.getReason()
                        );
                        transactionRepository.save(tx);
                    }
                }
            }
        }

        // Update original sale status if exists
        if (returnRecord.getSaleId() != null) {
            saleRepository.findById(returnRecord.getSaleId()).ifPresent(sale -> {
                sale.setStatus("PARTIALLY_RETURNED");
                saleRepository.save(sale);
            });
        }

        ReturnRecord saved = returnRepository.save(returnRecord);
        return ResponseEntity.ok(ApiResponse.success("Return processed successfully", saved));
    }

    @GetMapping("/returns")
    public ResponseEntity<List<ReturnRecord>> getReturns() {
        return ResponseEntity.ok(returnRepository.findAllByOrderByCreatedAtDesc());
    }
}
