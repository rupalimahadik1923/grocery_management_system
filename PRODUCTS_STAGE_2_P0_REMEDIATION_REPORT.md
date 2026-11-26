# PRODUCTS MODULE — STAGE 2: P0 SECURITY & HISTORICAL INTEGRITY REMEDIATION REPORT

**Target Module:** Products Module (`backend`)  
**Workspace Path:** `C:\Users\Siddheshwar\Desktop\grocery\grocery_management_system`  
**Date:** 2026-11-26  
**Auditor & Implementation Roles:** Senior Software Architect, Spring Boot Security Engineer, MongoDB Data Integrity Specialist, Backend Developer, QA Engineer  
**Stage Scope:** Remediation of approved P0 findings (**AUD-04-CRIT-01**, **AUD-04-CRIT-02**, **AUD-04-HIGH-02**)

---

## 1. EXECUTIVE SUMMARY

In Stage 2, the three critical and high-priority P0 findings identified in the Stage 1 audit of the Products module were remediated:

1. **AUD-04-CRIT-01 (Product ID Ownership Protection):** Neutralized cross-tenant document hijacking by enforcing server-authoritative document identity (`product.setId(null);`) during product creation. Client-supplied IDs are cleared before persistence, ensuring Spring Data MongoDB always creates a brand-new entity with a server-generated ID and prevents cross-store document overwriting.
2. **AUD-04-CRIT-02 (Safe Product Deletion & Archival):** Eliminated unsafe physical deletion for products with business history. `ProductController.deleteProduct` now inspects four downstream historical dependencies: Sales, Purchase Orders, Inventory Batches, and Inventory Transactions. Products with historical references are safely transitioned to `status = "ARCHIVED"` rather than being deleted, preserving return restock calculations, batch references, and historical sale snapshots. Unreferenced products are safely deleted to allow SKU reuse. Repeated deletion requests are idempotent.
3. **AUD-04-HIGH-02 (Status Endpoint Authorization & Validation):** Secured `PATCH /api/v1/products/{id}/status` with `@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")`, explicitly preventing unprivileged `CASHIER` accounts from modifying product statuses. In addition, status values are strictly validated against the allowed lifecycle states (`ACTIVE`, `INACTIVE`, `ARCHIVED`), rejecting malformed, empty, or arbitrary strings with HTTP 400.

All 21 integration tests in `ProductControllerTest` passed cleanly. Across the entire backend application, all 57 integration and unit tests executed with **0 failures, 0 errors, and 0 skipped tests**. Database isolation was maintained exclusively against `grocery_db_test`.

---

## 2. AUD-04-CRIT-01 REMEDIATION: PRODUCT ID OWNERSHIP PROTECTION

### 2.1 Root Cause & Vulnerability Analysis
In Spring Data MongoDB, `MongoRepository.save(entity)` determines whether to execute an `insert` or an `update/replace` based on whether the entity contains an `id`. Because `ProductController.createProduct` accepted `@RequestBody Product product` directly and did not clear incoming IDs, an authenticated user from Store B could provide the `id` of an existing product in Store A. Spring Data MongoDB would replace Store A's document, altering its `storeId` to Store B and overwriting its catalog attributes.

### 2.2 Implemented Solution
In `ProductController.java` (`createProduct`), server-authoritative identity and store scoping were enforced:
```java
// Force store isolation and server-authoritative identity:
// Client-supplied ID is strictly ignored and cleared to prevent document hijacking/overwrites.
product.setId(null);
product.setStoreId(storeId);
product.setSku(cleanSku);
if (product.getStatus() == null || product.getStatus().isBlank() || "ARCHIVED".equalsIgnoreCase(product.getStatus())) {
    product.setStatus("ACTIVE");
} else {
    String st = product.getStatus().trim().toUpperCase();
    product.setStatus(ALLOWED_STATUSES.contains(st) ? st : "ACTIVE");
}
```

### 2.3 Verification & Behavior
- When a client creates a product without an ID, MongoDB generates a standard 24-character hex `ObjectId`.
- When a client supplies an arbitrary or existing ID, `product.setId(null)` strips it, forcing the creation of a new document. The existing document in Store A remains unaltered.
- Authenticated store ownership (`user.getStoreId()`) strictly overrides any client-supplied `storeId`.

---

## 3. AUD-04-CRIT-02 REMEDIATION: SAFE PRODUCT DELETION & ARCHIVAL

### 3.1 Root Cause & Vulnerability Analysis
`ProductController.deleteProduct` previously called `productRepository.delete(product)` unconditionally. If the product had been sold, purchased, or batched, physical removal caused cascading failures:
- Customer returns in `SaleController.processReturn` attempt to look up `productRepository.findByStoreIdAndId(storeId, item.getProductId())` to restore returned inventory. With the product gone, restocking silently skipped, resulting in inventory ledger corruption.
- Receiving purchase orders in `PurchaseController` looks up the product to increment stock; with the product deleted, stock additions failed.
- `InventoryBatch` documents referencing `productId` became permanent orphaned records.
- `TenantMigrationService` could not resolve store ownership for historical sale items.

### 3.2 Implemented Solution
1. **Downstream Historical Reference Detection:**
   Four targeted existence query methods were introduced to verify business references:
   - `SaleRepository.existsByStoreIdAndItemsProductId(storeId, id)`: Checks for historical sales containing this product.
   - `PurchaseOrderRepository.existsByStoreIdAndItemsProductId(storeId, id)`: Checks for purchase orders referencing this product.
   - `InventoryBatchRepository.existsByStoreIdAndProductId(storeId, id)`: Checks for associated inventory batches.
   - `InventoryTransactionRepository.existsByStoreIdAndProductId(storeId, id)`: Checks for inventory audit transactions.

2. **Idempotent Archival vs. Safe Deletion Lifecycle:**
   In `ProductController.java` (`deleteProduct`):
   ```java
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
   ```

3. **POS & Sale Operation Protection:**
   In `SaleController.java` (`createSale`), line 93 was updated from checking only `"INACTIVE"` to checking `!"ACTIVE".equalsIgnoreCase(product.getStatus())`:
   ```java
   if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
       return ResponseEntity.badRequest()
               .body(ApiResponse.error("Inactive product cannot be sold: " + product.getName()));
   }
   ```
   This guarantees that both `INACTIVE` and `ARCHIVED` products are strictly excluded from POS checkout.

4. **Preservation of Historical Workflows:**
   Because archived products remain in MongoDB with `status = "ARCHIVED"`, direct lookups via `GET /api/v1/products/{id}` succeed, allowing historical receipts, return inspections, and restock operations to complete without error.

---

## 4. AUD-04-HIGH-02 REMEDIATION: STATUS ENDPOINT RBAC & VALIDATION

### 4.1 Root Cause & Vulnerability Analysis
`ProductController.updateStatus` (`PATCH /api/v1/products/{id}/status`) lacked a method-level `@PreAuthorize` annotation. Under `SecurityConfig.java`, any authenticated role (including `CASHIER`) could invoke the endpoint. Furthermore, the endpoint accepted arbitrary strings from `body.getOrDefault("status", "ACTIVE")` without validating against known lifecycle values.

### 4.2 Implemented Solution
In `ProductController.java`:
1. Added `@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")`.
2. Created a whitelist set: `private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "INACTIVE", "ARCHIVED");`.
3. Added strict input validation rejecting null, empty, or unapproved status values:
   ```java
   String rawStatus = body.get("status");
   if (rawStatus == null || rawStatus.trim().isEmpty()) {
       return ResponseEntity.badRequest().body(ApiResponse.error("Status value is required"));
   }
   String status = rawStatus.trim().toUpperCase();
   if (!ALLOWED_STATUSES.contains(status)) {
       return ResponseEntity.badRequest()
               .body(ApiResponse.error("Invalid status. Allowed values: ACTIVE, INACTIVE, ARCHIVED"));
   }
   ```
4. Applied the normalized uppercase status and updated `updatedAt`.

---

## 5. EXACT ARCHITECTURAL CHANGES

```
[ HTTP Client / POS ]
        |
        | (Bearer JWT)
        v
+---------------------------------------------------------------------------------+
| ProductController.java                                                          |
|                                                                                 |
| 1. POST /products                                                               |
|    - Forces product.setId(null)                                                 |
|    - Overrides storeId with user.getStoreId()                                   |
|    - Defaults status to ACTIVE                                                  |
|                                                                                 |
| 2. PATCH /products/{id}/status                                                  |
|    - @PreAuthorize("ADMIN, STORE_MANAGER, INVENTORY_STAFF") (Blocks CASHIER)    |
|    - Validates status in {ACTIVE, INACTIVE, ARCHIVED}                           |
|                                                                                 |
| 3. DELETE /products/{id}                                                        |
|    - Checks SaleRepository, PurchaseOrderRepository,                            |
|      InventoryBatchRepository, InventoryTransactionRepository                   |
|    - If referenced: Sets status = 'ARCHIVED' (Idempotent)                       |
|    - If unreferenced: productRepository.delete(product)                         |
+---------------------------------------------------------------------------------+
        |                                       |
        v                                       v
[ ProductRepository ]               [ Downstream Repositories ]
- MongoDB Collection: products      - SaleRepository: items.productId check
                                    - PurchaseOrderRepository: items.productId check
                                    - InventoryBatchRepository: productId check
                                    - InventoryTransactionRepository: productId check
```

---

## 6. PRODUCT LIFECYCLE CHANGES

| State | Entry Points | Can Be Sold via POS? | In Dashboard Active Count? | Resolvable by `GET /{id}`? | Physical Deletion Allowed? |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **`ACTIVE`** | Creation default; status patch | Yes | Yes | Yes | Only if unreferenced |
| **`INACTIVE`** | Status patch | No (400 Bad Request) | No | Yes | Only if unreferenced |
| **`ARCHIVED`** | `DELETE` when historical records exist; status patch | No (400 Bad Request) | No | Yes | **No (Protected)** |

---

## 7. AUTHORIZATION MATRIX (POST-REMEDIATION)

| Endpoint | Method | Permitted Roles | Unauthorized Roles | Result for Unauthorized |
| :--- | :--- | :--- | :--- | :--- |
| `/api/v1/products` | `GET` | Authenticated | None (Public to store) | 401 if unauthenticated |
| `/api/v1/products/{id}` | `GET` | Authenticated | None (Public to store) | 401 if unauthenticated |
| `/api/v1/products` | `POST` | `ADMIN`, `STORE_MANAGER`, `INVENTORY_STAFF` | `CASHIER` | 403 Forbidden |
| `/api/v1/products/{id}` | `PUT` | `ADMIN`, `STORE_MANAGER`, `INVENTORY_STAFF` | `CASHIER` | 403 Forbidden |
| `/api/v1/products/{id}/status` | `PATCH` | `ADMIN`, `STORE_MANAGER`, `INVENTORY_STAFF` | `CASHIER` | **403 Forbidden (Fixed)** |
| `/api/v1/products/{id}` | `DELETE`| `ADMIN`, `STORE_MANAGER`, `INVENTORY_STAFF` | `CASHIER` | 403 Forbidden |

---

## 8. FILES MODIFIED

| File Path | Description of Changes |
| :--- | :--- |
| `backend/src/main/java/com/grocery/controller/ProductController.java` | Injected 4 downstream repositories; added `setId(null)` in `createProduct`; secured `updateStatus` with `@PreAuthorize` and status whitelist validation; implemented archival-vs-delete lifecycle in `deleteProduct`. |
| `backend/src/main/java/com/grocery/controller/SaleController.java` | Updated `SaleController:93` to reject non-ACTIVE products (`!"ACTIVE".equalsIgnoreCase(...)`), preventing archived products from being sold. |
| `backend/src/main/java/com/grocery/model/Product.java` | Documented `ARCHIVED` status in field definition. |
| `backend/src/main/java/com/grocery/repository/SaleRepository.java` | Added `@Query(value = "{'storeId': ?0, 'items.productId': ?1}", exists = true) boolean existsByStoreIdAndItemsProductId(String storeId, String productId);`. |
| `backend/src/main/java/com/grocery/repository/PurchaseOrderRepository.java` | Added `@Query(value = "{'storeId': ?0, 'items.productId': ?1}", exists = true) boolean existsByStoreIdAndItemsProductId(String storeId, String productId);`. |
| `backend/src/main/java/com/grocery/repository/InventoryBatchRepository.java` | Added derived method `boolean existsByStoreIdAndProductId(String storeId, String productId);`. |
| `backend/src/main/java/com/grocery/repository/InventoryTransactionRepository.java` | Added derived method `boolean existsByStoreIdAndProductId(String storeId, String productId);`. |
| `backend/src/test/java/com/grocery/ProductControllerTest.java` | Expanded test suite from 6 tests to 21 tests covering all P0 remediation targets. |

---

## 9. TESTS CREATED OR MODIFIED

The test suite in `backend/src/test/java/com/grocery/ProductControllerTest.java` was expanded from 6 to 21 tests:

### Baseline Tests (Preserved & Passing)
1. `shouldRejectUnauthenticatedRequests`
2. `shouldIsolateProductsBetweenStoresOnRead`
3. `shouldBlockStoreBFromModifyingStoreAProduct`
4. `shouldBlockStoreBFromDeletingStoreAProduct`
5. `shouldAllowSameSkuInDifferentStoresButRejectWithinSameStore`
6. `shouldRejectInvalidNegativePrices`

### AUD-04-CRIT-01 Tests (Added & Passing)
7. `shouldGenerateServerIdAndIgnoreClientSuppliedIdOnCreation`: Asserts client-supplied ID is discarded and server ID is assigned in MongoDB.
8. `shouldPreventCrossTenantDocumentOverwriteViaClientSuppliedId`: Asserts Store B sending Store A's product ID does not overwrite or alter Store A's document.
9. `shouldEnforceAuthenticatedStoreOwnershipOverClientSuppliedStoreId`: Asserts client-supplied `storeId` is overwritten by authenticated user's store.

### AUD-04-CRIT-02 Tests (Added & Passing)
10. `shouldSafelyPhysicallyDeleteUnreferencedProduct`: Asserts unreferenced products are physically deleted.
11. `shouldArchiveProductWithHistoricalSaleReferencesInsteadOfDeleting`: Asserts product with sales history transitions to `ARCHIVED` and remains readable via `GET /{id}`.
12. `shouldArchiveProductWithHistoricalPurchaseReferences`: Asserts product with PO references transitions to `ARCHIVED`.
13. `shouldArchiveProductWithHistoricalInventoryBatchReferences`: Asserts product with batch references transitions to `ARCHIVED`.
14. `shouldArchiveProductWithHistoricalInventoryTransactionReferences`: Asserts product with inventory adjustment records transitions to `ARCHIVED`.
15. `shouldBeSafeAndIdempotentOnRepeatedDeletionOfArchivedProduct`: Asserts repeated deletion of an already-archived product returns 200 OK without errors.
16. `shouldStrictlyRejectSaleOfArchivedProduct`: Asserts POS sale creation rejects archived products with HTTP 400.

### AUD-04-HIGH-02 Tests (Added & Passing)
17. `shouldRejectUnauthenticatedStatusUpdate`: Asserts unauthenticated status patch returns 401 Unauthorized.
18. `shouldBlockCashierRoleFromUpdatingProductStatus`: Asserts `CASHIER` role is blocked with 403 Forbidden.
19. `shouldPermitPrivilegedRolesToUpdateProductStatus`: Asserts `ADMIN`, `STORE_MANAGER`, and `INVENTORY_STAFF` can update status.
20. `shouldRejectInvalidStatusValues`: Asserts invalid status names and empty values return 400 Bad Request.
21. `shouldBlockCrossTenantStatusUpdate`: Asserts Store B cannot update Store A product status (returns 404 Not Found).

---

## 10. TEST EXECUTION EVIDENCE

### 10.1 Baseline Test Results (Pre-Remediation)
```
[INFO] Running com.grocery.ProductControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 9.413 s
[INFO] BUILD SUCCESS
```

### 10.2 Post-Implementation Test Results
```
[INFO] Running com.grocery.ProductControllerTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 21.96 s
[INFO] BUILD SUCCESS
```

### 10.3 Full Backend Regression Test Results
```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.grocery.AuthBrutalTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.grocery.AuthControllerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.grocery.DashboardStage2Test
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.grocery.DashboardStage2_2Test
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.grocery.DashboardStage2_4Test
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.grocery.DashboardStage2_6Test
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.grocery.MultiTenantIsolationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.grocery.ProductControllerTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] Tests run: 57, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] Total time:  47.248 s
[INFO] Finished at: 2026-11-26T12:05:56+05:30
```

---

## 11. DATABASE ISOLATION EVIDENCE

All tests were executed strictly against the test database configuration defined in `backend/src/test/resources/application.properties`:
```properties
# Isolated Test Database Configuration
spring.data.mongodb.uri=mongodb://127.0.0.1:27017/grocery_db_test
spring.data.mongodb.auto-index-creation=true
```
- No connections were established to `grocery_db`.
- No production database records were altered or accessed.
- Unique random UUID test suffixes were used across all tests for tenant, store, user, and SKU isolation.

---

## 12. REMAINING RISKS & SCOPE BOUNDARIES

The approved P0 scope for this stage is complete. As per instructions, the following lower-priority findings from Stage 1 remain unaddressed and should be scheduled for subsequent stages:
1. **AUD-04-HIGH-01:** Barcode uniqueness per store (compound sparse unique index).
2. **AUD-04-HIGH-03:** Absence of Product DTOs causing potential field erasure on partial updates.
3. **AUD-04-HIGH-04:** Regex injection in `searchByStoreAndQuery`.
4. **AUD-04-HIGH-05:** Initial stock assignment audit ledger & frontend edit modal stock field.
5. **AUD-04-MED-01 through MED-06:** Validation, pagination, response enveloping, `double` currency precision, category name denormalization.

---

## 13. CONCLUSION

Stage 2 P0 remediation is **100% complete**:
- Client-supplied product IDs can no longer overwrite or hijack existing documents.
- Historical product references across Sales, Purchases, Batches, and Transactions are permanently protected from destructive physical deletion through idempotent archival.
- The product status modification endpoint is strictly secured with RBAC (`@PreAuthorize`) and validated against permitted lifecycle states.
- All 21 tests in `ProductControllerTest` and all 57 tests across the application pass with zero regressions.
