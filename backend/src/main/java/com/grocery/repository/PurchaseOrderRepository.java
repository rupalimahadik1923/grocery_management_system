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
}
