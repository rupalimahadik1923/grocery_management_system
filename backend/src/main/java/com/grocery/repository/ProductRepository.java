package com.grocery.repository;

import com.grocery.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {
    Optional<Product> findBySku(String sku);
    Optional<Product> findByBarcode(String barcode);
    List<Product> findByCategoryId(String categoryId);
    List<Product> findByStatus(String status);

    @Query("{'$or': [{'name': {'$regex': ?0, '$options': 'i'}}, {'sku': {'$regex': ?0, '$options': 'i'}}, {'barcode': {'$regex': ?0, '$options': 'i'}}]}")
    List<Product> searchProducts(String query);

    @Query("{'$expr': {'$lte': ['$currentStock', '$minStockLevel']}}")
    List<Product> findLowStockProducts();
}
