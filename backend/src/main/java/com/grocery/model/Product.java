package com.grocery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "products")
@CompoundIndexes({
    @CompoundIndex(name = "store_sku_unique_idx", def = "{'storeId': 1, 'sku': 1}", unique = true)
})
public class Product {

    @Id
    private String id;

    private String name;

    @Indexed
    private String sku;

    @Indexed
    private String barcode;

    @Indexed
    private String storeId;

    @Indexed
    private String categoryId;
    private String categoryName;

    private String unit; // PCS, KG, G, LITER, PACK
    private double costPrice;
    private double sellingPrice;
    private double currentStock;
    private double minStockLevel = 10.0;
    private double taxRate = 5.0; // GST %
    private String description;
    private String status = "ACTIVE"; // ACTIVE, INACTIVE

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Product() {}

    public Product(String name, String sku, String barcode, String categoryId, String categoryName,
                   String unit, double costPrice, double sellingPrice, double currentStock, double minStockLevel) {
        this.name = name;
        this.sku = sku;
        this.barcode = barcode;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.unit = unit;
        this.costPrice = costPrice;
        this.sellingPrice = sellingPrice;
        this.currentStock = currentStock;
        this.minStockLevel = minStockLevel;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public double getCostPrice() { return costPrice; }
    public void setCostPrice(double costPrice) { this.costPrice = costPrice; }

    public double getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(double sellingPrice) { this.sellingPrice = sellingPrice; }

    public double getCurrentStock() { return currentStock; }
    public void setCurrentStock(double currentStock) { this.currentStock = currentStock; }

    public double getMinStockLevel() { return minStockLevel; }
    public void setMinStockLevel(double minStockLevel) { this.minStockLevel = minStockLevel; }

    public double getTaxRate() { return taxRate; }
    public void setTaxRate(double taxRate) { this.taxRate = taxRate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }
}
