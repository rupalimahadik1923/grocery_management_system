package com.grocery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Document(collection = "sales")
@CompoundIndexes({
    @CompoundIndex(name = "storeId_createdAt_idx", def = "{'storeId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "storeId_receiptNumber_idx", def = "{'storeId': 1, 'receiptNumber': 1}")
})
public class Sale {

    @Id
    private String id;

    @Indexed
    private String storeId;

    @Indexed(unique = true)
    private String receiptNumber;

    private List<SaleItem> items = new ArrayList<>();

    private double subtotal;
    private double discountPercent;
    private double discountAmount;
    private double cgst; // Central GST
    private double sgst; // State GST
    private double grandTotal;

    private String paymentMethod = "CASH"; // CASH, UPI, CARD
    private double tenderedAmount;
    private double changeDue;

    private String cashierId;
    private String cashierName;

    private String customerId;
    private String customerPhone;
    private String customerName = "Walk-in Customer";

    private String status = "COMPLETED"; // COMPLETED, REFUNDED, PARTIALLY_RETURNED
    private double refundedTotal = 0.0;
    private double pendingRefundTotal = 0.0;
    private Map<String, Double> activeReservations = new HashMap<>();
    private Map<String, ReservationMetadata> reservationMetadata = new HashMap<>();
    private Map<String, Double> itemReturnedQty = new HashMap<>();
    private Map<String, Double> itemPendingReturnQty = new HashMap<>();
    private Set<String> appliedSettlementIds = new HashSet<>();

    public static class ReservationMetadata {
        private String status = "RESERVED"; // RESERVED, LINKED, RECOVERY_CLAIMED, RELEASED
        private long createdAtEpochMs = System.currentTimeMillis();
        private Map<String, Double> itemQuantities = new HashMap<>();

        public ReservationMetadata() {}

        public ReservationMetadata(String status, long createdAtEpochMs, Map<String, Double> itemQuantities) {
            this.status = status;
            this.createdAtEpochMs = createdAtEpochMs;
            this.itemQuantities = itemQuantities != null ? itemQuantities : new HashMap<>();
        }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAtEpochMs() { return createdAtEpochMs; }
        public void setCreatedAtEpochMs(long createdAtEpochMs) { this.createdAtEpochMs = createdAtEpochMs; }
        public Map<String, Double> getItemQuantities() { return itemQuantities; }
        public void setItemQuantities(Map<String, Double> itemQuantities) { this.itemQuantities = itemQuantities; }
    }

    @Indexed
    private LocalDateTime createdAt = LocalDateTime.now();
    @Indexed
    private Instant createdAtInstant;

    public static class SaleItem {
        private String saleItemId = UUID.randomUUID().toString();
        private String productId;
        private String productName;
        private String sku;
        private double unitPrice;
        private double quantity;
        private double taxRate;
        private double lineTotal;
        private Double costPrice; // Historical cost snapshot at time of sale (nullable for legacy records)

        public SaleItem() {}

        public SaleItem(String productId, String productName, String sku, double unitPrice, double quantity, double taxRate) {
            this.productId = productId;
            this.productName = productName;
            this.sku = sku;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
            this.taxRate = taxRate;
            this.lineTotal = unitPrice * quantity;
        }

        public String getSaleItemId() { return saleItemId; }
        public void setSaleItemId(String saleItemId) { this.saleItemId = saleItemId; }

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }

        public double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

        public double getQuantity() { return quantity; }
        public void setQuantity(double quantity) { this.quantity = quantity; }

        public double getTaxRate() { return taxRate; }
        public void setTaxRate(double taxRate) { this.taxRate = taxRate; }

        public double getLineTotal() { return lineTotal; }
        public void setLineTotal(double lineTotal) { this.lineTotal = lineTotal; }

        public Double getCostPrice() { return costPrice; }
        public void setCostPrice(Double costPrice) { this.costPrice = costPrice; }
    }

    public Sale() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }

    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }

    public List<SaleItem> getItems() { return items; }
    public void setItems(List<SaleItem> items) { this.items = items; }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    public double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(double discountPercent) { this.discountPercent = discountPercent; }

    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }

    public double getCgst() { return cgst; }
    public void setCgst(double cgst) { this.cgst = cgst; }

    public double getSgst() { return sgst; }
    public void setSgst(double sgst) { this.sgst = sgst; }

    public double getGrandTotal() { return grandTotal; }
    public void setGrandTotal(double grandTotal) { this.grandTotal = grandTotal; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public double getTenderedAmount() { return tenderedAmount; }
    public void setTenderedAmount(double tenderedAmount) { this.tenderedAmount = tenderedAmount; }

    public double getChangeDue() { return changeDue; }
    public void setChangeDue(double changeDue) { this.changeDue = changeDue; }

    public String getCashierId() { return cashierId; }
    public void setCashierId(String cashierId) { this.cashierId = cashierId; }

    public String getCashierName() { return cashierName; }
    public void setCashierName(String cashierName) { this.cashierName = cashierName; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getRefundedTotal() { return refundedTotal; }
    public void setRefundedTotal(double refundedTotal) { this.refundedTotal = refundedTotal; }

    public double getPendingRefundTotal() { return pendingRefundTotal; }
    public void setPendingRefundTotal(double pendingRefundTotal) { this.pendingRefundTotal = pendingRefundTotal; }

    public Map<String, Double> getActiveReservations() { return activeReservations; }
    public void setActiveReservations(Map<String, Double> activeReservations) { this.activeReservations = activeReservations; }

    public Map<String, ReservationMetadata> getReservationMetadata() { return reservationMetadata; }
    public void setReservationMetadata(Map<String, ReservationMetadata> reservationMetadata) { this.reservationMetadata = reservationMetadata; }

    public Map<String, Double> getItemReturnedQty() { return itemReturnedQty; }
    public void setItemReturnedQty(Map<String, Double> itemReturnedQty) { this.itemReturnedQty = itemReturnedQty; }

    public Map<String, Double> getItemPendingReturnQty() { return itemPendingReturnQty; }
    public void setItemPendingReturnQty(Map<String, Double> itemPendingReturnQty) { this.itemPendingReturnQty = itemPendingReturnQty; }

    public Set<String> getAppliedSettlementIds() { return appliedSettlementIds; }
    public void setAppliedSettlementIds(Set<String> appliedSettlementIds) { this.appliedSettlementIds = appliedSettlementIds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Instant getCreatedAtInstant() { return createdAtInstant; }
    public void setCreatedAtInstant(Instant createdAtInstant) { this.createdAtInstant = createdAtInstant; }
}
