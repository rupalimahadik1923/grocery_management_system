package com.grocery.dto;

import com.grocery.model.UserRole;
import com.grocery.model.UserStatus;

public class LoginResponse {

    private String token;
    private String tokenType = "Bearer";
    private String id;
    private String username;
    private String fullName;
    private String email;
    private UserRole role;
    private UserStatus status;

    public LoginResponse() {}

    public LoginResponse(String token, String id, String username, String fullName, String email, UserRole role, UserStatus status) {
        this.token = token;
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.status = status;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
}
