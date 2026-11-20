package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.Product;
import com.grocery.repository.CategoryRepository;
import com.grocery.repository.ProductRepository;
import com.grocery.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private UserPrincipal resolvePrincipal(UserPrincipal principal) {
        if (principal != null) return principal;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<?> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = resolvePrincipal(principal);
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized: store context missing"));
        }
        String storeId = user.getStoreId();

        if (category != null && !category.isEmpty() && !"ALL".equalsIgnoreCase(category)) {
            return ResponseEntity.ok(productRepository.findByStoreIdAndCategoryId(storeId, category));
        }
        if (status != null && !status.isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            return ResponseEntity.ok(productRepository.findByStoreIdAndStatus(storeId, status));
        }
        return ResponseEntity.ok(productRepository.findByStoreId(storeId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = resolvePrincipal(principal);
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return productRepository.findByStoreIdAndId(user.getStoreId(), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchProducts(
            @RequestParam String q,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = resolvePrincipal(principal);
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        return ResponseEntity.ok(productRepository.searchByStoreAndQuery(user.getStoreId(), q));
    }

    @GetMapping("/check-sku")
    public ResponseEntity<?> checkSku(
            @RequestParam String sku,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = resolvePrincipal(principal);
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        boolean exists = productRepository.existsByStoreIdAndSku(user.getStoreId(), sku.trim());
        return ResponseEntity.ok(Map.of("exists", exists, "sku", sku.trim()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Product>> createProduct(
            @RequestBody Product product,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = resolvePrincipal(principal);
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized: store context missing"));
        }
        String storeId = user.getStoreId();

        // 1. Validation: Prices must be non-negative
        if (product.getCostPrice() < 0 || product.getSellingPrice() < 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Cost price and selling price cannot be negative"));
        }

        // 2. Validation: SKU is required
        if (product.getSku() == null || product.getSku().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("SKU is required"));
        }
        String cleanSku = product.getSku().trim();

        // 3. Validation: SKU uniqueness within this store
        if (productRepository.existsByStoreIdAndSku(storeId, cleanSku)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("SKU already exists in this store"));
        }

        // 4. Force store isolation: set storeId from authenticated context (never from client body)
        product.setStoreId(storeId);
        product.setSku(cleanSku);
        if (product.getBarcode() != null) {
            product.setBarcode(product.getBarcode().trim());
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
    public ResponseEntity<ApiResponse<Product>> updateProduct(
            @PathVariable String id,
            @RequestBody Product update,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = resolvePrincipal(principal);
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized: store context missing"));
        }
        String storeId = user.getStoreId();

        // 1. Verify existence and store ownership
        return productRepository.findByStoreIdAndId(storeId, id).map(existing -> {
            // 2. Validate prices
            if (update.getCostPrice() < 0 || update.getSellingPrice() < 0) {
                return ResponseEntity.badRequest().body(ApiResponse.<Product>error("Cost price and selling price cannot be negative"));
            }

            // 3. Validate SKU uniqueness if SKU is changing
            if (update.getSku() != null) {
                String cleanSku = update.getSku().trim();
                if (!cleanSku.equalsIgnoreCase(existing.getSku()) && productRepository.existsByStoreIdAndSku(storeId, cleanSku)) {
                    return ResponseEntity.badRequest().body(ApiResponse.<Product>error("SKU already exists in this store"));
                }
                existing.setSku(cleanSku);
            }

            existing.setName(update.getName());
            if (update.getBarcode() != null) {
                existing.setBarcode(update.getBarcode().trim());
            }
            existing.setCategoryId(update.getCategoryId());
            existing.setCategoryName(update.getCategoryName());
            existing.setUnit(update.getUnit());
            existing.setCostPrice(update.getCostPrice());
            existing.setSellingPrice(update.getSellingPrice());
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
    public ResponseEntity<ApiResponse<Product>> updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = resolvePrincipal(principal);
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }

        String status = body.getOrDefault("status", "ACTIVE");
        return productRepository.findByStoreIdAndId(user.getStoreId(), id).map(product -> {
            product.setStatus(status);
            product.setUpdatedAt(LocalDateTime.now());
            Product saved = productRepository.save(product);
            return ResponseEntity.ok(ApiResponse.success("Product status updated to " + status, saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = resolvePrincipal(principal);
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }

        return productRepository.findByStoreIdAndId(user.getStoreId(), id).map(product -> {
            productRepository.delete(product);
            return ResponseEntity.ok(ApiResponse.<Void>success("Product deleted successfully", null));
        }).orElse(ResponseEntity.notFound().build());
    }
}
