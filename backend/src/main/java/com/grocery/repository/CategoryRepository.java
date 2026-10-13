package com.grocery.repository;

import com.grocery.model.Category;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends MongoRepository<Category, String> {
    Optional<Category> findByName(String name);
    List<Category> findByActiveOrderByDisplayOrderAsc(boolean active);
    boolean existsByName(String name);
}
