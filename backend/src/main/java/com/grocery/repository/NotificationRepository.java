package com.grocery.repository;

import com.grocery.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findAllByOrderByCreatedAtDesc();
    List<Notification> findByReadOrderByCreatedAtDesc(boolean read);
    long countByRead(boolean read);

    // Tenant-scoped queries
    Optional<Notification> findByStoreIdAndId(String storeId, String id);
    List<Notification> findByStoreIdOrderByCreatedAtDesc(String storeId);
    List<Notification> findByStoreIdAndReadOrderByCreatedAtDesc(String storeId, boolean read);
    long countByStoreIdAndRead(String storeId, boolean read);
}
