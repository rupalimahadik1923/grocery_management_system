package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.Expense;
import com.grocery.repository.ExpenseRepository;
import com.grocery.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/expenses")
@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
public class ExpenseController {

    private final ExpenseRepository expenseRepository;
    private final TenantContext tenantContext;

    public ExpenseController(ExpenseRepository expenseRepository, TenantContext tenantContext) {
        this.expenseRepository = expenseRepository;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    public ResponseEntity<List<Expense>> getExpenses(@AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return ResponseEntity.ok(expenseRepository.findByStoreIdOrderByExpenseDateDesc(storeId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Expense>> recordExpense(
            @RequestBody Expense expense,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        String username = tenantContext.requireUsername(userDetails);
        expense.setStoreId(storeId);
        expense.setRecordedBy(username);
        expense.setCreatedAt(LocalDateTime.now());
        if (expense.getExpenseDate() == null) {
            expense.setExpenseDate(LocalDate.now());
        }
        Expense saved = expenseRepository.save(expense);
        return ResponseEntity.ok(ApiResponse.success("Expense recorded successfully", saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return expenseRepository.findByStoreIdAndId(storeId, id).map(existing -> {
            expenseRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.<Void>success("Expense entry deleted", null));
        }).orElse(ResponseEntity.notFound().build());
    }
}
