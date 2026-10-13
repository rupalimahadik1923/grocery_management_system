package com.grocery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "store_settings")
public class StoreSettings {

    @Id
    private String id = "STORE_SETTINGS_PRIMARY";

    private String storeName = "FreshGrocery Supermarket";
    private String legalName = "FreshGrocery Retail Pvt Ltd";
    private String phone = "+91 98765 43210";
    private String email = "support@freshgrocery.local";
    private String address = "Shop 12-14, Green Valley Commercial Complex";
    private String city = "Bengaluru";
    private String state = "Karnataka";
    private String pinCode = "560001";
    private String gstin = "29ABCDE1234F1Z5";

    private String currencySymbol = "₹";
    private String receiptHeader = "FreshGrocery Supermarket\nEat Fresh, Live Healthy";
    private String receiptFooter = "Thank you for shopping with us!\nItems once sold can be returned within 3 days with receipt.";
    private boolean printBarcodeOnReceipt = true;
    private int printerPaperWidth = 80; // 58 or 80mm

    private double defaultTaxRate = 5.0;
    private double defaultMinStockThreshold = 10.0;
    private int nearExpiryDaysWindow = 30;
    private boolean allowNegativeStock = false;
    private boolean loyaltyEnabled = true;
    private double loyaltyPointsPerHundred = 1.0; // 1 pt per 100 Rs spent

    private LocalDateTime updatedAt = LocalDateTime.now();

    public StoreSettings() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getPinCode() { return pinCode; }
    public void setPinCode(String pinCode) { this.pinCode = pinCode; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public String getCurrencySymbol() { return currencySymbol; }
    public void setCurrencySymbol(String currencySymbol) { this.currencySymbol = currencySymbol; }

    public String getReceiptHeader() { return receiptHeader; }
    public void setReceiptHeader(String receiptHeader) { this.receiptHeader = receiptHeader; }

    public String getReceiptFooter() { return receiptFooter; }
    public void setReceiptFooter(String receiptFooter) { this.receiptFooter = receiptFooter; }

    public boolean isPrintBarcodeOnReceipt() { return printBarcodeOnReceipt; }
    public void setPrintBarcodeOnReceipt(boolean printBarcodeOnReceipt) { this.printBarcodeOnReceipt = printBarcodeOnReceipt; }

    public int getPrinterPaperWidth() { return printerPaperWidth; }
    public void setPrinterPaperWidth(int printerPaperWidth) { this.printerPaperWidth = printerPaperWidth; }

    public double getDefaultTaxRate() { return defaultTaxRate; }
    public void setDefaultTaxRate(double defaultTaxRate) { this.defaultTaxRate = defaultTaxRate; }

    public double getDefaultMinStockThreshold() { return defaultMinStockThreshold; }
    public void setDefaultMinStockThreshold(double defaultMinStockThreshold) { this.defaultMinStockThreshold = defaultMinStockThreshold; }

    public int getNearExpiryDaysWindow() { return nearExpiryDaysWindow; }
    public void setNearExpiryDaysWindow(int nearExpiryDaysWindow) { this.nearExpiryDaysWindow = nearExpiryDaysWindow; }

    public boolean isAllowNegativeStock() { return allowNegativeStock; }
    public void setAllowNegativeStock(boolean allowNegativeStock) { this.allowNegativeStock = allowNegativeStock; }

    public boolean isLoyaltyEnabled() { return loyaltyEnabled; }
    public void setLoyaltyEnabled(boolean loyaltyEnabled) { this.loyaltyEnabled = loyaltyEnabled; }

    public double getLoyaltyPointsPerHundred() { return loyaltyPointsPerHundred; }
    public void setLoyaltyPointsPerHundred(double loyaltyPointsPerHundred) { this.loyaltyPointsPerHundred = loyaltyPointsPerHundred; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
