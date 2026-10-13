package com.grocery.repository;

import com.grocery.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findAllByOrderByCreatedAtDesc();
    List<Notification> findByReadOrderByCreatedAtDesc(boolean read);
    long countByRead(boolean read);
}
