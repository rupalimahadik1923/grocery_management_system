package com.grocery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "expenses")
@CompoundIndex(name = "storeId_expenseDate_idx", def = "{'storeId': 1, 'expenseDate': -1}")
public class Expense {

    @Id
    private String id;

    @Indexed
    private String storeId;

    private String title;

    @Indexed
    private String category; // Rent, Electricity/Water, Salaries, Maintenance, Packaging, Spoilage, Transport, Other

    private double amount;
    private String paymentMethod = "CASH"; // CASH, BANK_TRANSFER, UPI

    @Indexed
    private LocalDate expenseDate = LocalDate.now();

    private String voucherRef;
    private String notes;
    private String recordedBy;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Expense() {}

    public Expense(String title, String category, double amount, String paymentMethod,
                   LocalDate expenseDate, String voucherRef, String notes, String recordedBy) {
        this.title = title;
        this.category = category;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.expenseDate = expenseDate != null ? expenseDate : LocalDate.now();
        this.voucherRef = voucherRef;
        this.notes = notes;
        this.recordedBy = recordedBy;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }

    public String getVoucherRef() { return voucherRef; }
    public void setVoucherRef(String voucherRef) { this.voucherRef = voucherRef; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
