package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.Product;
import com.grocery.repository.CategoryRepository;
import com.grocery.repository.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductController(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public ResponseEntity<List<Product>> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        if (category != null && !category.isEmpty()) {
            return ResponseEntity.ok(productRepository.findByCategoryId(category));
        }
        if (status != null && !status.isEmpty()) {
            return ResponseEntity.ok(productRepository.findByStatus(status));
        }
        return ResponseEntity.ok(productRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable String id) {
        return productRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Product>> searchProducts(@RequestParam String q) {
        return ResponseEntity.ok(productRepository.searchProducts(q));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Product>> createProduct(@RequestBody Product product) {
        if (product.getSku() != null && productRepository.findBySku(product.getSku()).isPresent()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("SKU already exists"));
        }
        if (product.getCategoryId() != null) {
            categoryRepository.findById(product.getCategoryId())
                    .ifPresent(cat -> product.setCategoryName(cat.getName()));
        }
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        Product saved = productRepository.save(product);
        return ResponseEntity.ok(ApiResponse.success("Product created successfully", saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> updateProduct(@PathVariable String id, @RequestBody Product update) {
        return productRepository.findById(id).map(existing -> {
            existing.setName(update.getName());
            existing.setSku(update.getSku());
            existing.setBarcode(update.getBarcode());
            existing.setCategoryId(update.getCategoryId());
            existing.setCategoryName(update.getCategoryName());
            existing.setUnit(update.getUnit());
            existing.setCostPrice(update.getCostPrice());
            existing.setSellingPrice(update.getSellingPrice());
            existing.setCurrentStock(update.getCurrentStock());
            existing.setMinStockLevel(update.getMinStockLevel());
            existing.setTaxRate(update.getTaxRate());
            existing.setDescription(update.getDescription());
            existing.setStatus(update.getStatus());
            existing.setUpdatedAt(LocalDateTime.now());
            Product saved = productRepository.save(existing);
            return ResponseEntity.ok(ApiResponse.success("Product updated successfully", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Product>> updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "ACTIVE");
        return productRepository.findById(id).map(product -> {
            product.setStatus(status);
            product.setUpdatedAt(LocalDateTime.now());
            Product saved = productRepository.save(product);
            return ResponseEntity.ok(ApiResponse.success("Product status updated to " + status, saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable String id) {
        productRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }
}
