package com.grocery.dto;

import com.grocery.model.UserRole;
import com.grocery.model.UserStatus;

public class UserResponse {

    private String id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private UserRole role;
    private UserStatus status;

    public UserResponse() {}

    public UserResponse(String id, String username, String fullName, String email, String phone, UserRole role, UserStatus status) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
}
