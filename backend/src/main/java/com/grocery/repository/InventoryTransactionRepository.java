package com.grocery.repository;

import com.grocery.model.InventoryTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryTransactionRepository extends MongoRepository<InventoryTransaction, String> {
    List<InventoryTransaction> findTop50ByOrderByCreatedAtDesc();
    List<InventoryTransaction> findByProductIdOrderByCreatedAtDesc(String productId);
}
