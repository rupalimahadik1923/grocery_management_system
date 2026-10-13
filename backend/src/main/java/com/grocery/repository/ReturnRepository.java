package com.grocery.repository;

import com.grocery.model.ReturnRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnRepository extends MongoRepository<ReturnRecord, String> {
    Optional<ReturnRecord> findByReturnNumber(String returnNumber);
    List<ReturnRecord> findBySaleId(String saleId);
    List<ReturnRecord> findAllByOrderByCreatedAtDesc();
}
