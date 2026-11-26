package com.grocery.repository;

import com.grocery.model.Supplier;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends MongoRepository<Supplier, String> {
    Optional<Supplier> findByCode(String code);
    Optional<Supplier> findByName(String name);

    // Tenant-scoped queries
    List<Supplier> findByStoreId(String storeId);
    Optional<Supplier> findByStoreIdAndId(String storeId, String id);
    boolean existsByStoreIdAndCode(String storeId, String code);
    long countByStoreId(String storeId);
}
