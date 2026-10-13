package com.grocery.repository;

import com.grocery.model.Sale;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaleRepository extends MongoRepository<Sale, String> {
    Optional<Sale> findByReceiptNumber(String receiptNumber);
    List<Sale> findAllByOrderByCreatedAtDesc();
    List<Sale> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);
    List<Sale> findTop20ByOrderByCreatedAtDesc();
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
