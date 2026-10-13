package com.grocery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notifications")
public class Notification {

    @Id
    private String id;

    private String title;
    private String message;
    private String type; // LOW_STOCK, EXPIRING_SOON, PURCHASE_ARRIVAL, SYSTEM
    private String priority = "MEDIUM"; // LOW, MEDIUM, HIGH, CRITICAL

    @Indexed
    private boolean read = false;

    private String targetLink;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification() {}

    public Notification(String title, String message, String type, String priority, String targetLink) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.priority = priority;
        this.targetLink = targetLink;
        this.read = false;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public String getTargetLink() { return targetLink; }
    public void setTargetLink(String targetLink) { this.targetLink = targetLink; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
