package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.Expense;
import com.grocery.repository.ExpenseRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseRepository expenseRepository;

    public ExpenseController(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @GetMapping
    public ResponseEntity<List<Expense>> getExpenses() {
        return ResponseEntity.ok(expenseRepository.findAllByOrderByExpenseDateDesc());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Expense>> recordExpense(
            @RequestBody Expense expense,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "Staff";
        expense.setRecordedBy(username);
        expense.setCreatedAt(LocalDateTime.now());
        if (expense.getExpenseDate() == null) {
            expense.setExpenseDate(LocalDate.now());
        }
        Expense saved = expenseRepository.save(expense);
        return ResponseEntity.ok(ApiResponse.success("Expense recorded successfully", saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(@PathVariable String id) {
        expenseRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Expense entry deleted", null));
    }
}
