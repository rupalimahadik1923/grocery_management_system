package com.grocery.repository;

import com.grocery.model.Expense;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends MongoRepository<Expense, String> {
    List<Expense> findAllByOrderByExpenseDateDesc();
    List<Expense> findByExpenseDateBetweenOrderByExpenseDateDesc(LocalDate start, LocalDate end);

    // Tenant-scoped queries
    Optional<Expense> findByStoreIdAndId(String storeId, String id);
    List<Expense> findByStoreIdOrderByExpenseDateDesc(String storeId);
    @Query(value = "{'storeId': ?0, 'expenseDate': {'$gte': ?1, '$lt': ?2}}", sort = "{'expenseDate': -1}")
    List<Expense> findByStoreIdAndExpenseDateGreaterThanEqualAndExpenseDateLessThanOrderByExpenseDateDesc(String storeId, LocalDate startInclusive, LocalDate endExclusive);
}
