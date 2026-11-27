package com.grocery.repository;

import com.grocery.model.Sale;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    // Tenant-scoped queries
    Optional<Sale> findByStoreIdAndId(String storeId, String id);
    Optional<Sale> findByStoreIdAndReceiptNumber(String storeId, String receiptNumber);
    Optional<Sale> findByStoreIdAndIdempotencyKey(String storeId, String idempotencyKey);
    List<Sale> findByStoreIdOrderByCreatedAtDesc(String storeId);
    List<Sale> findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(String storeId, LocalDateTime start, LocalDateTime end);
    @Query(value = "{'storeId': ?0, 'createdAt': {'$gte': ?1, '$lt': ?2}}", sort = "{'createdAt': -1}")
    List<Sale> findByStoreIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(String storeId, LocalDateTime startInclusive, LocalDateTime endExclusive);
    @Query(value = "{'storeId': ?0, '$or': [{'createdAtInstant': {'$gte': ?1, '$lt': ?2}}, {'createdAtInstant': null, 'createdAt': {'$gte': ?3, '$lt': ?4}}]}", sort = "{'createdAt': -1}")
    List<Sale> findByStoreIdAndIstRange(String storeId, Instant startInstant, Instant endInstant, LocalDateTime startLocalFallback, LocalDateTime endLocalFallback);
    List<Sale> findTop20ByStoreIdOrderByCreatedAtDesc(String storeId);

    @Query(value = "{'storeId': ?0, 'items.productId': ?1}", exists = true)
    boolean existsByStoreIdAndItemsProductId(String storeId, String productId);
}
