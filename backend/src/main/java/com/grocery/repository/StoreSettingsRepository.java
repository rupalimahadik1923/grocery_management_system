package com.grocery.repository;

import com.grocery.model.StoreSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreSettingsRepository extends MongoRepository<StoreSettings, String> {
}
