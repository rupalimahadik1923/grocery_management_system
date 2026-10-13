package com.grocery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "purchase_orders")
public class PurchaseOrder {

    @Id
    private String id;

    @Indexed(unique = true)
    private String orderNumber;

    @Indexed
    private String supplierId;
    private String supplierName;

    private LocalDate orderDate = LocalDate.now();
    private LocalDate expectedDeliveryDate;

    private List<PurchaseOrderItem> items = new ArrayList<>();
    private double totalAmount;

    @Indexed
    private String status = "DRAFT"; // DRAFT, ORDERED, RECEIVED, CANCELLED
    private String notes;
    private LocalDateTime createdAt = LocalDateTime.now();

    public static class PurchaseOrderItem {
        private String productId;
        private String productName;
        private double orderQty;
        private double unitCost;
        private double totalCost;

        public PurchaseOrderItem() {}

        public PurchaseOrderItem(String productId, String productName, double orderQty, double unitCost) {
            this.productId = productId;
            this.productName = productName;
            this.orderQty = orderQty;
            this.unitCost = unitCost;
            this.totalCost = orderQty * unitCost;
        }

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public double getOrderQty() { return orderQty; }
        public void setOrderQty(double orderQty) { this.orderQty = orderQty; }

        public double getUnitCost() { return unitCost; }
        public void setUnitCost(double unitCost) { this.unitCost = unitCost; }

        public double getTotalCost() { return totalCost; }
        public void setTotalCost(double totalCost) { this.totalCost = totalCost; }
    }

    public PurchaseOrder() {}

    public PurchaseOrder(String orderNumber, String supplierId, String supplierName,
                         LocalDate expectedDeliveryDate, List<PurchaseOrderItem> items, String notes) {
        this.orderNumber = orderNumber;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.items = items != null ? items : new ArrayList<>();
        this.notes = notes;
        this.status = "DRAFT";
        this.createdAt = LocalDateTime.now();
        this.totalAmount = this.items.stream().mapToDouble(PurchaseOrderItem::getTotalCost).sum();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public LocalDate getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }

    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(LocalDate expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }

    public List<PurchaseOrderItem> getItems() { return items; }
    public void setItems(List<PurchaseOrderItem> items) {
        this.items = items;
        this.totalAmount = items != null ? items.stream().mapToDouble(PurchaseOrderItem::getTotalCost).sum() : 0.0;
    }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
