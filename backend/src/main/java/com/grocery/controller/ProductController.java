package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.Category;
import com.grocery.model.Product;
import com.grocery.repository.*;
import com.grocery.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SaleRepository saleRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;

    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "INACTIVE", "ARCHIVED");

    public ProductController(ProductRepository productRepository,
                             CategoryRepository categoryRepository,
                             SaleRepository saleRepository,
                             PurchaseOrderRepository purchaseOrderRepository,
                             InventoryBatchRepository inventoryBatchRepository,
                             InventoryTransactionRepository inventoryTransactionRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.saleRepository = saleRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.inventoryBatchRepository = inventoryBatchRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
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
            @RequestParam(required = false) String excludeId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = resolvePrincipal(principal);
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }
        String cleanSku = sku != null ? sku.trim().toUpperCase() : "";
        if (cleanSku.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("SKU cannot be empty"));
        }
        
        boolean exists;
        if (excludeId != null && !excludeId.trim().isEmpty()) {
            exists = productRepository.findByStoreIdAndSku(user.getStoreId(), cleanSku)
                    .map(p -> !p.getId().equals(excludeId.trim()))
                    .orElse(false);
        } else {
            exists = productRepository.existsByStoreIdAndSku(user.getStoreId(), cleanSku);
        }
        return ResponseEntity.ok(Map.of("exists", exists, "sku", cleanSku));
    }

    private static final int MAX_NAME_LENGTH = 150;
    private static final int MAX_SKU_LENGTH = 50;
    private static final int MAX_BARCODE_LENGTH = 50;
    private static final int MAX_DESC_LENGTH = 1000;
    private static final double MAX_PRICE_VALUE = 1_000_000.0; // 10 Lakhs max per item
    private static final double MAX_STOCK_VALUE = 1_000_000.0;

    private static boolean containsControlCharacters(String str) {
        if (str == null) return false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            // Allow standard tab, newline, carriage return, reject all other control chars (e.g. \0)
            if (Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r') {
                return true;
            }
        }
        return false;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<Product>> createProduct(
            @RequestBody com.grocery.dto.ProductCreateRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = resolvePrincipal(principal);
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized: store context missing"));
        }
        String storeId = user.getStoreId();

        // 1. Validation: Product Name (Required, bounds, control chars)
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Product name is required"));
        }
        String cleanName = req.getName().trim();
        if (cleanName.length() > MAX_NAME_LENGTH) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Product name cannot exceed " + MAX_NAME_LENGTH + " characters"));
        }
        if (containsControlCharacters(cleanName)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Product name cannot contain control characters"));
        }

        // 2. Validation: SKU (Required, bounds, control chars, uppercase)
        if (req.getSku() == null || req.getSku().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("SKU is required"));
        }
        String cleanSku = req.getSku().trim().toUpperCase();
        if (cleanSku.length() > MAX_SKU_LENGTH) {
            return ResponseEntity.badRequest().body(ApiResponse.error("SKU cannot exceed " + MAX_SKU_LENGTH + " characters"));
        }
        if (containsControlCharacters(cleanSku)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("SKU cannot contain control characters"));
        }

        // 3. Validation: SKU uniqueness within this store
        if (productRepository.existsByStoreIdAndSku(storeId, cleanSku)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("SKU already exists in this store"));
        }

        // 4. Validation: Prices (Nullability, non-negative, bounds)
        double costPrice = req.getCostPrice() != null ? req.getCostPrice() : 0.0;
        double sellingPrice = req.getSellingPrice() != null ? req.getSellingPrice() : 0.0;
        if (costPrice < 0 || sellingPrice < 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Cost price and selling price cannot be negative"));
        }
        if (costPrice > MAX_PRICE_VALUE || sellingPrice > MAX_PRICE_VALUE) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Price exceeds maximum permitted limit"));
        }

        // 5. Validation: Initial Stock & Thresholds
        double initialStock = req.getCurrentStock() != null ? req.getCurrentStock() : 0.0;
        if (initialStock < 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Initial stock cannot be negative"));
        }
        if (initialStock > MAX_STOCK_VALUE) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Initial stock exceeds maximum permitted limit"));
        }
        double minStock = req.getMinStockLevel() != null ? req.getMinStockLevel() : 10.0;
        if (minStock < 0 || minStock > MAX_STOCK_VALUE) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Min stock level is out of allowed range"));
        }
        double taxRate = req.getTaxRate() != null ? req.getTaxRate() : 5.0;
        if (taxRate < 0 || taxRate > 100.0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Tax rate must be between 0% and 100%"));
        }

        // 6. Validation: Barcode (Trimming, control chars, length, null conversion)
        String cleanBarcode = null;
        if (req.getBarcode() != null) {
            String trimmed = req.getBarcode().trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.length() > MAX_BARCODE_LENGTH) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Barcode cannot exceed " + MAX_BARCODE_LENGTH + " characters"));
                }
                if (containsControlCharacters(trimmed)) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Barcode cannot contain control characters"));
                }
                cleanBarcode = trimmed;
            }
        }

        // 7. Validation: Description length & control chars
        String cleanDesc = null;
        if (req.getDescription() != null) {
            String trimmed = req.getDescription().trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.length() > MAX_DESC_LENGTH) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Description cannot exceed " + MAX_DESC_LENGTH + " characters"));
                }
                cleanDesc = trimmed;
            }
        }

        // 8. Validation: Status (Explicit rejection of invalid or ARCHIVED status)
        String finalStatus = "ACTIVE";
        if (req.getStatus() != null && !req.getStatus().trim().isEmpty()) {
            String candidate = req.getStatus().trim().toUpperCase();
            if ("ARCHIVED".equalsIgnoreCase(candidate) || "DELETED".equalsIgnoreCase(candidate)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("New products cannot be created in ARCHIVED or DELETED status"));
            }
            if (!ALLOWED_STATUSES.contains(candidate)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid product status. Allowed: ACTIVE, INACTIVE"));
            }
            finalStatus = candidate;
        }

        // 9. Validation: Category (Authoritative derivation from DB, tenant check, active check)
        String categoryId = null;
        String categoryName = null;
        if (req.getCategoryId() != null && !req.getCategoryId().trim().isEmpty()) {
            String requestedCatId = req.getCategoryId().trim();
            Category cat;
            try {
                cat = categoryRepository.findByStoreIdAndId(storeId, requestedCatId).orElse(null);
            } catch (Exception ex) {
                // Catches malformed ObjectId representations without crashing
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid category ID format"));
            }
            if (cat == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Category does not belong to your store"));
            }
            if (!cat.isActive()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Cannot assign an inactive category"));
            }
            categoryId = cat.getId();
            categoryName = cat.getName();
        }

        // 10. Construct persistent Product entity with server-authoritative context
        Product product = new Product();
        product.setId(null); // Clear any client-supplied ID
        product.setStoreId(storeId); // Enforce authenticated store context
        product.setName(cleanName);
        product.setSku(cleanSku);
        product.setBarcode(cleanBarcode);
        product.setCategoryId(categoryId);
        product.setCategoryName(categoryName);
        product.setUnit(req.getUnit() != null && !req.getUnit().trim().isEmpty() ? req.getUnit().trim() : "PACK");
        product.setCostPrice(costPrice);
        product.setSellingPrice(sellingPrice);
        product.setCurrentStock(initialStock);
        product.setMinStockLevel(minStock);
        product.setTaxRate(taxRate);
        product.setDescription(cleanDesc);
        product.setStatus(finalStatus);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        try {
            Product saved = productRepository.save(product);
            return ResponseEntity.ok(ApiResponse.success("Product created successfully", saved));
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // Guard against concurrent duplicate SKU race condition
            return ResponseEntity.badRequest().body(ApiResponse.error("SKU already exists in this store"));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<Product>> updateProduct(
            @PathVariable String id,
            @RequestBody com.grocery.dto.ProductUpdateRequest update,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = resolvePrincipal(principal);
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized: store context missing"));
        }
        String storeId = user.getStoreId();

        // 1. Verify existence and store ownership
        return productRepository.findByStoreIdAndId(storeId, id).map(existing -> {
            // Cannot update an ARCHIVED product via regular PUT
            if ("ARCHIVED".equalsIgnoreCase(existing.getStatus())) {
                return ResponseEntity.badRequest().body(ApiResponse.<Product>error("Cannot edit an archived product. Restore it first."));
            }

            // 2. Validate prices if provided
            if (update.getCostPrice() != null) {
                if (update.getCostPrice() < 0) {
                    return ResponseEntity.badRequest().body(ApiResponse.<Product>error("Cost price cannot be negative"));
                }
                existing.setCostPrice(update.getCostPrice());
            }
            if (update.getSellingPrice() != null) {
                if (update.getSellingPrice() < 0) {
                    return ResponseEntity.badRequest().body(ApiResponse.<Product>error("Selling price cannot be negative"));
                }
                existing.setSellingPrice(update.getSellingPrice());
            }

            // 3. Validate SKU uniqueness if SKU is changing
            if (update.getSku() != null && !update.getSku().trim().isEmpty()) {
                String cleanSku = update.getSku().trim().toUpperCase();
                if (!cleanSku.equalsIgnoreCase(existing.getSku())) {
                    if (productRepository.existsByStoreIdAndSku(storeId, cleanSku)) {
                        return ResponseEntity.badRequest().body(ApiResponse.<Product>error("SKU already exists in this store"));
                    }
                    existing.setSku(cleanSku);
                }
            }

            // 4. Update basic fields safely
            if (update.getName() != null && !update.getName().trim().isEmpty()) {
                existing.setName(update.getName().trim());
            }

            // Barcode normalization
            if (update.getBarcode() != null) {
                String trimmedBarcode = update.getBarcode().trim();
                existing.setBarcode(trimmedBarcode.isEmpty() ? null : trimmedBarcode);
            }

            // Category handling (server-authoritative resolution & explicit clearing)
            if (update.getCategoryId() != null) {
                String cleanCatId = update.getCategoryId().trim();
                if (cleanCatId.isEmpty()) {
                    // Intentionally clearing category
                    existing.setCategoryId(null);
                    existing.setCategoryName(null);
                } else {
                    Category cat = categoryRepository.findByStoreIdAndId(storeId, cleanCatId).orElse(null);
                    if (cat == null) {
                        return ResponseEntity.badRequest().body(ApiResponse.<Product>error("Category does not belong to your store"));
                    }
                    if (!cat.isActive() && (existing.getCategoryId() == null || !existing.getCategoryId().equals(cat.getId()))) {
                        return ResponseEntity.badRequest().body(ApiResponse.<Product>error("Cannot assign an inactive category"));
                    }
                    existing.setCategoryId(cat.getId());
                    existing.setCategoryName(cat.getName());
                }
            }

            if (update.getUnit() != null && !update.getUnit().trim().isEmpty()) {
                existing.setUnit(update.getUnit().trim());
            }
            if (update.getMinStockLevel() != null) {
                if (update.getMinStockLevel() < 0) {
                    return ResponseEntity.badRequest().body(ApiResponse.<Product>error("Min stock level cannot be negative"));
                }
                existing.setMinStockLevel(update.getMinStockLevel());
            }
            if (update.getTaxRate() != null) {
                if (update.getTaxRate() < 0) {
                    return ResponseEntity.badRequest().body(ApiResponse.<Product>error("Tax rate cannot be negative"));
                }
                existing.setTaxRate(update.getTaxRate());
            }
            if (update.getDescription() != null) {
                existing.setDescription(update.getDescription().trim());
            }

            // Lifecycle status: PUT cannot arbitrarily set ARCHIVED or bypass status transitions
            if (update.getStatus() != null && !update.getStatus().trim().isEmpty()) {
                String targetStatus = update.getStatus().trim().toUpperCase();
                if (!ALLOWED_STATUSES.contains(targetStatus)) {
                    return ResponseEntity.badRequest().body(ApiResponse.<Product>error("Invalid status value"));
                }
                if ("ARCHIVED".equalsIgnoreCase(targetStatus) && !"ARCHIVED".equalsIgnoreCase(existing.getStatus())) {
                    return ResponseEntity.badRequest().body(ApiResponse.<Product>error("Archival must be performed through the delete/archive endpoint"));
                }
                existing.setStatus(targetStatus);
            }

            // Current stock is strictly preserved (never overwritten by PUT)
            existing.setUpdatedAt(LocalDateTime.now());

            Product saved = productRepository.save(existing);
            return ResponseEntity.ok(ApiResponse.success("Product updated successfully", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<Product>> updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = resolvePrincipal(principal);
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }

        String rawStatus = body.get("status");
        if (rawStatus == null || rawStatus.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Status value is required"));
        }
        String status = rawStatus.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(status)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid status. Allowed values: ACTIVE, INACTIVE, ARCHIVED"));
        }

        return productRepository.findByStoreIdAndId(user.getStoreId(), id).map(product -> {
            product.setStatus(status);
            product.setUpdatedAt(LocalDateTime.now());
            Product saved = productRepository.save(product);
            return ResponseEntity.ok(ApiResponse.success("Product status updated to " + status, saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = resolvePrincipal(principal);
        if (user == null || user.getStoreId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized"));
        }
        String storeId = user.getStoreId();

        return productRepository.findByStoreIdAndId(storeId, id).map(product -> {
            boolean hasSales = saleRepository.existsByStoreIdAndItemsProductId(storeId, id);
            boolean hasPurchases = purchaseOrderRepository.existsByStoreIdAndItemsProductId(storeId, id);
            boolean hasBatches = inventoryBatchRepository.existsByStoreIdAndProductId(storeId, id);
            boolean hasTransactions = inventoryTransactionRepository.existsByStoreIdAndProductId(storeId, id);

            if (hasSales || hasPurchases || hasBatches || hasTransactions || "ARCHIVED".equalsIgnoreCase(product.getStatus())) {
                // Idempotent archival: if already archived, return success
                if ("ARCHIVED".equalsIgnoreCase(product.getStatus())) {
                    return ResponseEntity.ok(ApiResponse.<Void>success("Product is already archived", null));
                }
                // Preserve historical references: archive instead of physical deletion
                product.setStatus("ARCHIVED");
                product.setUpdatedAt(LocalDateTime.now());
                productRepository.save(product);
                return ResponseEntity.ok(ApiResponse.<Void>success("Product has historical records; archived successfully", null));
            } else {
                // Unreferenced product: safe to physically delete
                productRepository.delete(product);
                return ResponseEntity.ok(ApiResponse.<Void>success("Product deleted successfully", null));
            }
        }).orElse(ResponseEntity.notFound().build());
    }
}
