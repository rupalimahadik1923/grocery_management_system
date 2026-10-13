package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.Category;
import com.grocery.repository.CategoryRepository;
import com.grocery.repository.ProductRepository;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Category>> createCategory(@RequestBody Category category) {
        if (categoryRepository.existsByName(category.getName())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Category with name '" + category.getName() + "' already exists"));
        }
        Category saved = categoryRepository.save(category);
        return ResponseEntity.ok(ApiResponse.success("Category created successfully", saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Category>> updateCategory(@PathVariable String id, @RequestBody Category update) {
        return categoryRepository.findById(id).map(existing -> {
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
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable String id) {
        if (!productRepository.findByCategoryId(id).isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Cannot delete category with associated products. Deactivate instead."));
        }
        categoryRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted", null));
    }
}
