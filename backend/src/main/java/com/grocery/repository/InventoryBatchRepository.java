package com.grocery.repository;

import com.grocery.model.InventoryBatch;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface InventoryBatchRepository extends MongoRepository<InventoryBatch, String> {
    List<InventoryBatch> findByProductId(String productId);
    List<InventoryBatch> findByProductIdAndCurrentQtyGreaterThanOrderByExpiryDateAsc(String productId, double minQty);
    List<InventoryBatch> findByExpiryDateBetweenAndStatus(LocalDate startDate, LocalDate endDate, String status);
    List<InventoryBatch> findByStatus(String status);

    // Tenant-scoped queries
    List<InventoryBatch> findByStoreId(String storeId);
    List<InventoryBatch> findByStoreIdAndExpiryDateBetweenAndStatus(String storeId, LocalDate startDate, LocalDate endDate, String status);
    boolean existsByStoreIdAndProductId(String storeId, String productId);
}
