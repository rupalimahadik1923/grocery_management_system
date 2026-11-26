package com.grocery.repository;

import com.grocery.model.ReturnRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnRepository extends MongoRepository<ReturnRecord, String> {
    Optional<ReturnRecord> findByReturnNumber(String returnNumber);
    List<ReturnRecord> findBySaleId(String saleId);
    List<ReturnRecord> findAllByOrderByCreatedAtDesc();

    // Tenant-scoped queries
    Optional<ReturnRecord> findByStoreIdAndId(String storeId, String id);
    List<ReturnRecord> findByStoreIdOrderByCreatedAtDesc(String storeId);
    List<ReturnRecord> findByStoreIdAndSaleId(String storeId, String saleId);
    List<ReturnRecord> findByStoreIdAndSaleIdIn(String storeId, Collection<String> saleIds);
    Optional<ReturnRecord> findByStoreIdAndIdempotencyKey(String storeId, String idempotencyKey);
    Optional<ReturnRecord> findByStoreIdAndReservationId(String storeId, String reservationId);
    List<ReturnRecord> findByStoreIdAndStatusAndSaleSettled(String storeId, String status, boolean saleSettled);
}
