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

    // Store-scoped query methods
    List<Product> findByStoreId(String storeId);
    long countByStoreIdAndStatus(String storeId, String status);
    List<Product> findByStoreIdAndCategoryId(String storeId, String categoryId);
    List<Product> findByStoreIdAndStatus(String storeId, String status);
    Optional<Product> findByStoreIdAndId(String storeId, String id);
    Optional<Product> findByStoreIdAndSku(String storeId, String sku);
    Optional<Product> findByStoreIdAndBarcode(String storeId, String barcode);
    boolean existsByStoreIdAndSku(String storeId, String sku);

    @Query("{'storeId': ?0, '$or': [{'name': {'$regex': ?1, '$options': 'i'}}, {'sku': {'$regex': ?1, '$options': 'i'}}, {'barcode': {'$regex': ?1, '$options': 'i'}}]}")
    List<Product> searchByStoreAndQuery(String storeId, String query);

    @Query("{'storeId': ?0, '$expr': {'$lte': ['$currentStock', '$minStockLevel']}}")
    List<Product> findLowStockProductsByStore(String storeId);
}
