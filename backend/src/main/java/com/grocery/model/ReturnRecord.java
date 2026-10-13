package com.grocery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "returns")
public class ReturnRecord {

    @Id
    private String id;

    @Indexed(unique = true)
    private String returnNumber;

    @Indexed
    private String saleId;
    private String receiptNumber;

    private List<ReturnItem> items = new ArrayList<>();
    private double totalRefund;
    private String refundMethod = "CASH";
    private String processedBy;
    private String notes;
    private LocalDateTime createdAt = LocalDateTime.now();

    public static class ReturnItem {
        private String productId;
        private String productName;
        private double quantity;
        private double unitPrice;
        private double refundAmount;
        private String reason;
        private boolean restocked = true;

        public ReturnItem() {}

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public double getQuantity() { return quantity; }
        public void setQuantity(double quantity) { this.quantity = quantity; }

        public double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

        public double getRefundAmount() { return refundAmount; }
        public void setRefundAmount(double refundAmount) { this.refundAmount = refundAmount; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public boolean isRestocked() { return restocked; }
        public void setRestocked(boolean restocked) { this.restocked = restocked; }
    }

    public ReturnRecord() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getReturnNumber() { return returnNumber; }
    public void setReturnNumber(String returnNumber) { this.returnNumber = returnNumber; }

    public String getSaleId() { return saleId; }
    public void setSaleId(String saleId) { this.saleId = saleId; }

    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }

    public List<ReturnItem> getItems() { return items; }
    public void setItems(List<ReturnItem> items) { this.items = items; }

    public double getTotalRefund() { return totalRefund; }
    public void setTotalRefund(double totalRefund) { this.totalRefund = totalRefund; }

    public String getRefundMethod() { return refundMethod; }
    public void setRefundMethod(String refundMethod) { this.refundMethod = refundMethod; }

    public String getProcessedBy() { return processedBy; }
    public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
