package com.grocery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "inventory_batches")
@CompoundIndex(name = "prod_expiry_idx", def = "{'productId': 1, 'expiryDate': 1}")
public class InventoryBatch {

    @Id
    private String id;

    @Indexed
    private String productId;
    private String productName;

    @Indexed
    private String batchNumber;

    private String supplierId;
    private String supplierName;

    private LocalDate inwardDate;
    private LocalDate expiryDate;

    private double initialQty;
    private double currentQty;
    private double purchaseCost;

    private String status = "ACTIVE"; // ACTIVE, DEPLETED, EXPIRED
    private LocalDateTime createdAt = LocalDateTime.now();

    public InventoryBatch() {}

    public InventoryBatch(String productId, String productName, String batchNumber,
                          String supplierId, String supplierName, LocalDate inwardDate,
                          LocalDate expiryDate, double initialQty, double currentQty, double purchaseCost) {
        this.productId = productId;
        this.productName = productName;
        this.batchNumber = batchNumber;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.inwardDate = inwardDate;
        this.expiryDate = expiryDate;
        this.initialQty = initialQty;
        this.currentQty = currentQty;
        this.purchaseCost = purchaseCost;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public LocalDate getInwardDate() { return inwardDate; }
    public void setInwardDate(LocalDate inwardDate) { this.inwardDate = inwardDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public double getInitialQty() { return initialQty; }
    public void setInitialQty(double initialQty) { this.initialQty = initialQty; }

    public double getCurrentQty() { return currentQty; }
    public void setCurrentQty(double currentQty) { this.currentQty = currentQty; }

    public double getPurchaseCost() { return purchaseCost; }
    public void setPurchaseCost(double purchaseCost) { this.purchaseCost = purchaseCost; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
