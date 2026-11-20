package com.grocery.repository;

import com.grocery.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameAndStoreCode(String username, String storeCode);
    List<User> findByStoreId(String storeId);
    List<User> findByStoreCode(String storeCode);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);
    Boolean existsByUsernameAndStoreCode(String username, String storeCode);
}
