package com.grocery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "returns")
@CompoundIndexes({
    @CompoundIndex(name = "storeId_createdAt_idx", def = "{'storeId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "storeId_idempotencyKey_partial_idx", def = "{'storeId': 1, 'idempotencyKey': 1}", unique = true, partialFilter = "{'idempotencyKey': {'$type': 'string'}}")
})
public class ReturnRecord {

    @Id
    private String id;

    @Indexed
    private String storeId;

    @Indexed(unique = true)
    private String returnNumber;

    @Indexed
    private String saleId;
    private String receiptNumber;

    private List<ReturnItem> items = new ArrayList<>();
    private double totalRefund;
    private String refundMethod = "CASH";
    private String status = "PENDING"; // PENDING, COMPLETED, FAILED, REJECTED
    private String idempotencyKey;
    private String reservationId;
    private boolean saleSettled = false;
    private boolean restockProcessed = false;
    private String processedBy;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private String notes;
    private LocalDateTime createdAt = LocalDateTime.now();

    public static class ReturnItem {
        private String saleItemId;
        private String productId;
        private String productName;
        private double quantity;
        private double unitPrice;
        private double refundAmount;
        private String reason;
        private boolean restocked = true;

        public ReturnItem() {}

        public String getSaleItemId() { return saleItemId; }
        public void setSaleItemId(String saleItemId) { this.saleItemId = saleItemId; }

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

    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }

    public boolean isSaleSettled() { return saleSettled; }
    public void setSaleSettled(boolean saleSettled) { this.saleSettled = saleSettled; }

    public boolean isRestockProcessed() { return restockProcessed; }
    public void setRestockProcessed(boolean restockProcessed) { this.restockProcessed = restockProcessed; }

    public String getProcessedBy() { return processedBy; }
    public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }

    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }

    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
