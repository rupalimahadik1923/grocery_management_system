package com.grocery.repository;

import com.grocery.model.Store;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoreRepository extends MongoRepository<Store, String> {
    Optional<Store> findByStoreCode(String storeCode);
    Optional<Store> findByOwnerEmail(String ownerEmail);
    boolean existsByStoreCode(String storeCode);
    boolean existsByOwnerEmail(String ownerEmail);
}
