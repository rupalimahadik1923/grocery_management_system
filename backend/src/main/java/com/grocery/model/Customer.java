package com.grocery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "customers")
@CompoundIndex(name = "storeId_phone_unique_idx", def = "{'storeId': 1, 'phone': 1}", unique = true)
public class Customer {

    @Id
    private String id;

    @Indexed
    private String storeId;

    private String name;

    private String phone;

    private String email;
    private String address;
    private int loyaltyPoints = 0;
    private double totalSpent = 0.0;
    private int ordersCount = 0;
    private String tier = "BRONZE"; // BRONZE, SILVER, GOLD
    private LocalDateTime createdAt = LocalDateTime.now();

    public Customer() {}

    public Customer(String name, String phone, String email, String address) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.loyaltyPoints = 0;
        this.totalSpent = 0.0;
        this.ordersCount = 0;
        this.tier = "BRONZE";
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public int getLoyaltyPoints() { return loyaltyPoints; }
    public void setLoyaltyPoints(int loyaltyPoints) { this.loyaltyPoints = loyaltyPoints; }

    public double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(double totalSpent) { this.totalSpent = totalSpent; }

    public int getOrdersCount() { return ordersCount; }
    public void setOrdersCount(int ordersCount) { this.ordersCount = ordersCount; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
