package com.grocery.repository;

import com.grocery.model.Customer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends MongoRepository<Customer, String> {
    Optional<Customer> findByPhone(String phone);

    // Tenant-scoped queries
    List<Customer> findByStoreId(String storeId);
    Optional<Customer> findByStoreIdAndId(String storeId, String id);
    Optional<Customer> findByStoreIdAndPhone(String storeId, String phone);
}
