package com.grocery.dto;

import jakarta.validation.constraints.NotBlank;

public class StoreRegistrationRequest {

    @NotBlank(message = "Store name is required")
    private String storeName;

    @NotBlank(message = "Store code is required")
    private String storeCode;

    @NotBlank(message = "Owner name is required")
    private String ownerName;

    private String adminUsername; // Optional custom admin username (defaults to ownerName or storeCode + "_admin")

    @NotBlank(message = "Owner email is required")
    private String ownerEmail;

    @NotBlank(message = "Password is required")
    private String password;

    private String phone;
    private String city;
    private String address;
    private String gstin;

    public StoreRegistrationRequest() {}

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }

    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
}
