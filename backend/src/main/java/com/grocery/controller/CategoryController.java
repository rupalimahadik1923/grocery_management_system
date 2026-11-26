package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.Category;
import com.grocery.repository.CategoryRepository;
import com.grocery.repository.ProductRepository;
import com.grocery.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryController(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories(@AuthenticationPrincipal UserPrincipal user) {
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(categoryRepository.findByStoreId(user.getStoreId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<ApiResponse<Category>> createCategory(
            @RequestBody Category category,
            @AuthenticationPrincipal UserPrincipal user) {
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Store context missing"));
        }
        String storeId = user.getStoreId();
        if (categoryRepository.existsByNameAndStoreId(category.getName(), storeId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Category with name '" + category.getName() + "' already exists"));
        }
        category.setStoreId(storeId);
        Category saved = categoryRepository.save(category);
        return ResponseEntity.ok(ApiResponse.success("Category created successfully", saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<ApiResponse<Category>> updateCategory(
            @PathVariable String id,
            @RequestBody Category update,
            @AuthenticationPrincipal UserPrincipal user) {
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Store context missing"));
        }
        return categoryRepository.findByStoreIdAndId(user.getStoreId(), id).map(existing -> {
            existing.setName(update.getName());
            existing.setSlug(update.getSlug());
            existing.setIcon(update.getIcon());
            existing.setDescription(update.getDescription());
            existing.setDisplayOrder(update.getDisplayOrder());
            existing.setActive(update.isActive());
            Category saved = categoryRepository.save(existing);
            return ResponseEntity.ok(ApiResponse.success("Category updated successfully", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal user) {
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Store context missing"));
        }
        return categoryRepository.findByStoreIdAndId(user.getStoreId(), id).map(existing -> {
            if (!productRepository.findByStoreIdAndCategoryId(user.getStoreId(), id).isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.<Void>error("Cannot delete category with associated products. Deactivate instead."));
            }
            categoryRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.<Void>success("Category deleted", null));
        }).orElse(ResponseEntity.notFound().build());
    }
}
