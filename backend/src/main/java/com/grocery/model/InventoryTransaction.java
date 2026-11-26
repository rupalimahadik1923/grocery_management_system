package com.grocery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "inventory_transactions")
@CompoundIndex(name = "storeId_createdAt_idx", def = "{'storeId': 1, 'createdAt': -1}")
public class InventoryTransaction {

    @Id
    private String id;

    @Indexed
    private String storeId;

    @Indexed
    private String productId;
    private String productName;

    private String type; // OPENING_STOCK, PURCHASE_RECEIPT, SALE, CUSTOMER_RETURN, DAMAGE, EXPIRY, ADJUSTMENT
    private double quantity;
    private double balanceAfter;

    private String referenceType;
    private String referenceId;
    private String performedBy;
    private String notes;

    @Indexed
    private LocalDateTime createdAt = LocalDateTime.now();

    public InventoryTransaction() {}

    public InventoryTransaction(String productId, String productName, String type, double quantity,
                                double balanceAfter, String referenceType, String referenceId,
                                String performedBy, String notes) {
        this.productId = productId;
        this.productName = productName;
        this.type = type;
        this.quantity = quantity;
        this.balanceAfter = balanceAfter;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.performedBy = performedBy;
        this.notes = notes;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public double getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(double balanceAfter) { this.balanceAfter = balanceAfter; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
