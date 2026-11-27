package com.grocery.repository;

import com.grocery.model.PurchaseOrder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends MongoRepository<PurchaseOrder, String> {
    Optional<PurchaseOrder> findByOrderNumber(String orderNumber);
    List<PurchaseOrder> findByStatusOrderByCreatedAtDesc(String status);
    List<PurchaseOrder> findAllByOrderByCreatedAtDesc();

    // Tenant-scoped queries
    Optional<PurchaseOrder> findByStoreIdAndId(String storeId, String id);
    Optional<PurchaseOrder> findByStoreIdAndReceiveIdempotencyKey(String storeId, String receiveIdempotencyKey);
    List<PurchaseOrder> findByStoreIdAndStatusOrderByCreatedAtDesc(String storeId, String status);
    List<PurchaseOrder> findByStoreIdOrderByCreatedAtDesc(String storeId);
    long countByStoreId(String storeId);

    @org.springframework.data.mongodb.repository.Query(value = "{'storeId': ?0, 'items.productId': ?1}", exists = true)
    boolean existsByStoreIdAndItemsProductId(String storeId, String productId);
}
