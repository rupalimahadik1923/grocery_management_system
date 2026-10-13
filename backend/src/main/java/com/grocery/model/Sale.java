package com.grocery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "sales")
public class Sale {

    @Id
    private String id;

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

    @Indexed
    private LocalDateTime createdAt = LocalDateTime.now();

    public static class SaleItem {
        private String productId;
        private String productName;
        private String sku;
        private double unitPrice;
        private double quantity;
        private double taxRate;
        private double lineTotal;

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
    }

    public Sale() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
