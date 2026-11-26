# PRODUCTS MODULE — STAGE 1: COMPREHENSIVE READ-ONLY EVIDENCE AUDIT

**Target Module:** Products Module (`backend` & `frontend`)  
**Workspace Path:** `C:\Users\Siddheshwar\Desktop\grocery\grocery_management_system`  
**Audit Date:** 2026-11-26  
**Auditor Roles:** Senior Software Architect, Spring Boot Backend Engineer, MongoDB Data Integrity Specialist, Security Auditor, QA Engineer, Frontend Integration Reviewer  
**Audit Execution Mode:** READ-ONLY (Zero modifications made to code, database, migrations, or tests)

---

## 1. EXECUTIVE SUMMARY

An exhaustive, evidence-based, read-only audit of the **Products** module in the Grocery Management System was conducted across backend Java classes, database configurations, frontend HTML/JavaScript interfaces, test suites, and downstream dependencies (Sales, Purchases, Inventory, Batches, Returns, Reports, and Dashboard).

The Products module is a central anchor for the entire application. While baseline store-scoping exists for primary read/write routes, the audit identified **17 confirmed findings** (2 Critical, 5 High, 6 Medium, 4 Low) spanning data corruption, security bypasses, referential integrity breaks, and architectural gaps:

1. **CRITICAL — Cross-Tenant Product Hijacking via Client-Supplied ID:** `createProduct` accepts client JSON directly into `Product` without clearing `id`. In Spring Data MongoDB, `save()` performs a document replace/upsert when an ID is present, allowing a tenant to overwrite and hijack another store's existing product document.
2. **CRITICAL — Unrestricted Hard Deletion:** `deleteProduct` executes a physical `delete()` from MongoDB with zero referential integrity checks against existing Sales, Purchase Orders, Inventory Batches, Inventory Transactions, or Returns. When historical transactions attempt to resolve a deleted product, downstream restocking and verification operations fail.
3. **HIGH — Missing Barcode Uniqueness & Scanner Failure:** Barcodes are not unique in MongoDB (only single-field non-unique `@Indexed`) and are not checked for uniqueness on create or update. If duplicate barcodes are entered, POS scanning crashes with `IncorrectResultSizeDataAccessException` (500 Internal Server Error).
4. **HIGH — Missing `@PreAuthorize` on Status Patch:** `PATCH /api/v1/products/{id}/status` lacks `@PreAuthorize`, permitting unprivileged roles (such as `CASHIER`) to deactivate or reactivate products.
5. **HIGH — Silent Data Loss on Partial Updates:** `updateProduct` binds directly to `Product` without a DTO or partial-update support. Missing request payload fields silently overwrite existing values with primitive zeroes (`costPrice = 0.0`, `sellingPrice = 0.0`) or `null` (`unit`, `description`, `categoryId`).
6. **HIGH — Unsanitized Regex / ReDoS in Product Search:** `searchByStoreAndQuery` injects user input directly into MongoDB `$regex` with case-insensitivity, causing unhandled syntax crashes on regex characters and posing a Denial-of-Service (ReDoS) vulnerability.
7. **HIGH — Direct Initial Stock Assignment Bypassing Audit Trails:** `createProduct` allows arbitrary `currentStock` to be persisted directly without creating an `InventoryTransaction`. Conversely, `updateProduct` silently ignores client-submitted stock changes, deceiving users.

No code or configuration changes were executed during this audit. All findings are substantiated by exact source code citations.

---

## 2. MODULE ARCHITECTURE OVERVIEW

### 2.1 Component Breakdown

| Layer | File / Component | Responsibility | Current State / Architectural Deficiencies |
| :--- | :--- | :--- | :--- |
| **Model / Entity** | `backend/src/main/java/com/grocery/model/Product.java` | Core MongoDB entity mapping to collection `products` | Lacks Jakarta validation (`@NotNull`, `@Size`, etc.); lacks optimistic locking (`@Version`); uses primitive `double` for currency; lacks image fields. |
| **Repository** | `backend/src/main/java/com/grocery/repository/ProductRepository.java` | Spring Data MongoDB interface | Contains store-scoped queries and compound index, but retains legacy non-store-scoped methods. Search uses unescaped `$regex`. |
| **Service Layer** | *Non-existent* | Business logic orchestration | **Missing.** Business logic, validation, and entity mutation are directly implemented inside `ProductController`. |
| **Controller** | `backend/src/main/java/com/grocery/controller/ProductController.java` | REST API endpoints under `/api/v1/products` | Direct entity binding (no DTOs); missing `@PreAuthorize` on status toggle; no pagination; inconsistent response wrapping. |
| **Frontend UI** | `frontend/products.html` | Catalog management UI (table, search, add/edit modal, CSV export) | Loads entire catalog in-memory; search and filters are client-side only; edit modal submits `currentStock` which backend ignores. |
| **POS UI** | `frontend/pos.html` | Point of Sale product grid & scanner | Loads entire catalog; displays `INACTIVE` products, leading to checkout rejection. |
| **Security** | `backend/src/main/java/com/grocery/security/SecurityConfig.java` | JWT & Method Security | Requires authentication for `/api/v1/**`, but relies on `@PreAuthorize` on controller methods for RBAC. |

### 2.2 Domain Relationships & Data Flow

```
                      +-------------------+
                      |   Category.java   |
                      +-------------------+
                                | (categoryId, categoryName)
                                v
+----------------+    +-------------------+    +----------------------+
|  User / Store  |--->|   Product.java    |<---| InventoryBatch.java  |
+----------------+    +-------------------+    +----------------------+
                                |
             +------------------+------------------+
             |                                     |
             v                                     v
+------------------------+             +------------------------+
| Sale.java (SaleItem)   |             | PurchaseOrder.java     |
| (Snapshot pricing)     |             | (PurchaseOrderItem)    |
+------------------------+             +------------------------+
```

---

## 3. PRODUCT CRUD INTEGRITY (AUD-04-01)

### 3.1 Creation (`POST /api/v1/products`)
- **Validation Deficiencies:**
  - `ProductController.java:109-112`: Only checks `costPrice < 0 || sellingPrice < 0`. Zero is allowed.
  - `ProductController.java:115-117`: Checks `sku == null || sku.trim().isEmpty()`.
  - **No validation** on `name` (null or blank allowed).
  - **No validation** on `unit` (null or arbitrary string allowed).
  - **No validation** on `currentStock` (can be initialized to negative numbers).
  - **No validation** on `minStockLevel` (can be negative).
  - **No validation** on `taxRate` (can be negative or invalid percentage).
  - **No validation** on `status` (can be set to arbitrary strings).
  - **No validation** on `barcode` uniqueness.
- **Store & Ownership Assignment:**
  - `product.setStoreId(storeId)` correctly enforces tenant store ID from authenticated context.
  - **Critical Flaw:** `product.getId()` is NOT cleared (`product.setId(null)` is omitted). If the client supplies an `id`, `productRepository.save(product)` in MongoDB executes an upsert/replace, overwriting an existing document across tenant boundaries.
- **Category Association:**
  - `ProductController.java:132-138`: Validates category belongs to the store, but fails to check whether the category is active (`cat.isActive()`). A product can be created under a deactivated category.

### 3.2 Update (`PUT /api/v1/products/{id}`)
- **Tenant Scoping:** Correctly queries `productRepository.findByStoreIdAndId(storeId, id)`. Returns 404 if product does not exist in the caller's store.
- **Direct Entity Binding Flaw:**
  - `existing.setName(update.getName())` (overwrites with null if omitted).
  - `existing.setUnit(update.getUnit())` (overwrites with null if omitted).
  - `existing.setCostPrice(update.getCostPrice())` (overwrites with 0.0 if omitted).
  - `existing.setSellingPrice(update.getSellingPrice())` (overwrites with 0.0 if omitted).
  - `existing.setCategoryId(update.getCategoryId())` & `setCategoryName(...)` (cleared if omitted).
- **Update SKU Validation Discrepancy:**
  - `updateProduct` lines 166-172: Only checks `if (update.getSku() != null)`. It does NOT check `cleanSku.isEmpty()`. A user can update an existing product's SKU to an empty string `""`.
- **Barcode Validation:** Barcode uniqueness is completely unverified during updates.
- **Concurrency & Lost Updates:** `Product.java` lacks `@Version`. Concurrent updates suffer from blind last-write-wins overwriting.

### 3.3 Deletion & Archival (`DELETE /api/v1/products/{id}` & `PATCH /api/v1/products/{id}/status`)
- **Hard Deletion Risk:** `deleteProduct` executes `productRepository.delete(product)` directly. It does not check whether sales, purchases, returns, batches, or inventory logs reference the product ID.
- **Soft Deletion Absence:** No soft deletion flag (`isDeleted` or `DELETED` status) exists.
- **Status Toggle Authorization Defect:** `updateStatus` (`PATCH /{id}/status`) has no `@PreAuthorize` annotation, allowing `CASHIER` accounts to toggle product status.
- **Status Value Validation Absence:** Accepts arbitrary string values from client JSON (`body.getOrDefault("status", "ACTIVE")`) without validating against allowed enum/constants (`ACTIVE`, `INACTIVE`).

---

## 4. PRODUCT DATA & BUSINESS RULE INTEGRITY (AUD-04-02)

| Business Field | Storage Type | Validation in Controller | Identified Weakness / Risk |
| :--- | :--- | :--- | :--- |
| **`name`** | `String` | None | Blank, empty, or excessively long names can be stored. |
| **`sku`** | `String` | Non-blank on create; store uniqueness check | Update allows blanking; case-sensitive match permits duplicate casing variants. |
| **`barcode`** | `String` | Trimmed only | Uniqueness is neither checked in controller nor enforced by MongoDB index. |
| **`categoryId`** | `String` | Verified against store | Inactive categories are accepted; category renaming does not cascade to product. |
| **`categoryName`**| `String` | Set from category | Denormalized; becomes stale when category name is updated. |
| **`unit`** | `String` | None | String-based; not validated against valid unit types (`PCS`, `KG`, `G`, `LITER`, `PACK`). |
| **`costPrice`** | `double` | Checked `>= 0` | Primitive `double` subject to IEEE 754 precision loss; partial update resets to 0.0. |
| **`sellingPrice`**| `double` | Checked `>= 0` | Primitive `double`; no check if selling price is below cost price; resets to 0.0 on partial update. |
| **`currentStock`** | `double` | None on create; ignored on update | Initial stock bypasses `InventoryTransaction` ledger; update silently drops user input. |
| **`minStockLevel`**| `double` | None | Default 10.0; can be set negative. |
| **`taxRate`** | `double` | None | Default 5.0; can be set negative or > 100%. |
| **`description`** | `String` | None | Unsanitized text. |
| **`status`** | `String` | None | Accepts arbitrary strings; no enum constraint. |
| **`productImage`** | *None* | *None* | Field does not exist in data model or UI. |

---

## 5. TENANT ISOLATION & SECURITY (AUD-04-03)

### 5.1 Endpoint-by-Endpoint RBAC & Tenant Scoping Matrix

| HTTP Method | Endpoint | Allowed Roles (`@PreAuthorize`) | Tenant Resolution Method | Tenant Isolation Status | Vulnerability / Defect |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/products` | All Authenticated | `user.getStoreId()` | Isolated | None on tenant filter; lacks pagination. |
| `GET` | `/api/v1/products/{id}` | All Authenticated | `findByStoreIdAndId` | Isolated | Returns 404 for cross-tenant ID. |
| `GET` | `/api/v1/products/search` | All Authenticated | `searchByStoreAndQuery` | Isolated | Unescaped regex injection / ReDoS. |
| `GET` | `/api/v1/products/check-sku` | All Authenticated | `existsByStoreIdAndSku` | Isolated | Exact case match only. |
| `POST` | `/api/v1/products` | `ADMIN`, `STORE_MANAGER`, `INVENTORY_STAFF` | Overwrites `product.setStoreId` | **COMPROMISED** | Client-supplied `id` enables document overwriting / hijacking. |
| `PUT` | `/api/v1/products/{id}` | `ADMIN`, `STORE_MANAGER`, `INVENTORY_STAFF` | `findByStoreIdAndId` | Isolated | Overwriting/erasure of omitted fields. |
| `PATCH` | `/api/v1/products/{id}/status` | **NONE (Missing `@PreAuthorize`)** | `findByStoreIdAndId` | Isolated by Store | **RBAC Bypass:** `CASHIER` can toggle product status. |
| `DELETE`| `/api/v1/products/{id}` | `ADMIN`, `STORE_MANAGER`, `INVENTORY_STAFF` | `findByStoreIdAndId` | Isolated | Hard deletion destroys historical integrity. |

### 5.2 Latent Cross-Tenant Methods in Repository
`ProductRepository.java` contains legacy methods lacking `storeId`:
- `findBySku(String sku)`
- `findByBarcode(String barcode)`
- `findByCategoryId(String categoryId)`
- `findByStatus(String status)`
- `searchProducts(String query)`
- `findLowStockProducts()`

While not actively invoked by `ProductController`, their presence represents an ongoing architectural risk.

---

## 6. SKU, BARCODE & DATABASE INDEX INTEGRITY (AUD-04-04)

### 6.1 Index Analysis in `Product.java`
```java
@Document(collection = "products")
@CompoundIndexes({
    @CompoundIndex(name = "store_sku_unique_idx", def = "{'storeId': 1, 'sku': 1}", unique = true)
})
public class Product {
    @Indexed private String sku;         // REDUNDANT
    @Indexed private String barcode;     // NON-UNIQUE
    @Indexed private String storeId;     // REDUNDANT (covered by compound index prefix)
    @Indexed private String categoryId;
```

1. **Compound Index `store_sku_unique_idx`:** Enforces store-scoped SKU uniqueness at the database level. When race conditions occur, MongoDB throws `DuplicateKeyException` (handled by `GlobalExceptionHandler.java:39-43`).
2. **Redundant Single-Field Indexes:**
   - Single-field index on `sku` is redundant because `store_sku_unique_idx` can cover `storeId` and `sku`.
   - Single-field index on `storeId` is redundant because `storeId` is the prefix of `store_sku_unique_idx`.
3. **Missing Barcode Compound Unique Index:**
   - `@Indexed private String barcode` creates a standard non-unique index across all documents.
   - There is NO `{ storeId: 1, barcode: 1, unique: true, sparse: true }` index.
   - Duplicate barcodes are neither caught by application logic nor by database constraints.
4. **Case Sensitivity:**
   - MongoDB indexes are case-sensitive by default.
   - `existsByStoreIdAndSku(storeId, "sku-1")` and `existsByStoreIdAndSku(storeId, "SKU-1")` evaluate independently, allowing duplicate SKU entries differing only by casing.

---

## 7. FRONTEND & BACKEND CONTRACT (AUD-04-05)

### 7.1 Payload & Response Contract Mismatches

| Aspect | Backend Implementation (`ProductController.java`) | Frontend Implementation (`frontend/products.html`) | Contract Gap / Defect |
| :--- | :--- | :--- | :--- |
| **Response Wrapper** | `GET /products` returns raw `List<Product>`. `POST`/`PUT` return `ApiResponse<Product>`. | Handles both via `Array.isArray(res) ? res : res.data`. | Inconsistent API design forcing frontend defensiveness. |
| **Stock on Edit** | `updateProduct` does NOT touch `currentStock`. | Modal includes "Initial Stock Qty" in edit form and submits `currentStock`. | Stock edits in UI are silently ignored by the server. |
| **Server-side Search** | `GET /search?q=...` implemented. | Frontend searches in-memory array via `searchInput.addEventListener('input', renderTable)`. | Server search endpoint is unused by catalog UI. |
| **Server-side Filtering**| `GET /products?category=...&status=...` implemented. | Frontend filters in-memory array via `renderTable()`. | Server filter endpoints are unused by catalog UI. |
| **Pagination** | No pagination support (`List<Product>` returned). | Loads all items into single table DOM without pagination. | Performance degradation with catalog growth. |
| **Product Images** | Entity has no image fields. | UI has no image upload or preview. | Complete feature absence. |
| **CSV Export** | `ReportController` has `/api/v1/reports/export/products`. | UI generates CSV client-side using JavaScript Blob URL. | Redundant export implementations. |

---

## 8. DOWNSTREAM COMPATIBILITY & INTEGRITY RISKS (AUD-04-06)

### 8.1 Sales Module Compatibility
- **Snapshot Integrity:** `SaleItem` in `Sale.java` snapshots `productName`, `sku`, `unitPrice`, `taxRate`, and `costPrice`. Historical sale totals and profit metrics are preserved even if product prices change later.
- **Inactive Product Sales:** `SaleController.java:93` explicitly rejects inactive products (`"INACTIVE".equalsIgnoreCase(product.getStatus())`).
- **POS Catalog Flaw:** `frontend/pos.html:515-535` fails to filter `status === 'ACTIVE'`. Inactive products appear in the grid; attempting checkout results in a 400 error.
- **Deleted Product Restock Failure:** In `SaleController.java:430-435`, returns processing executes `productRepository.findByStoreIdAndId(storeId, item.getProductId())`. If the product was hard-deleted, `product` is null, restocking is silently skipped, and inventory counts become inaccurate.

### 8.2 Inventory Module Compatibility
- **Authoritative Source of Truth:** `Product.currentStock` is currently the authoritative live stock quantity queried by POS and dashboard. `InventoryTransaction` serves as an audit ledger.
- **Audit Ledger Disconnect:** Products created via `createProduct` set `currentStock` directly without inserting an initial `InventoryTransaction`.
- **Orphan Batches:** `InventoryBatch` documents reference `productId`. Hard deletion of a product leaves orphaned batches in MongoDB.

### 8.3 Purchase Order Module Compatibility
- **Ordering Inactive Products:** `PurchaseController.java:71-76` verifies `findByStoreIdAndId` but does NOT check `product.getStatus()`. Inactive products can be ordered and received.
- **Stock Receiving on Deleted Products:** `PurchaseController.java:127-133` looks up product by ID to add received stock. If the product was hard-deleted, receiving silently skips stock incrementation.

### 8.4 Dashboard & Reporting Compatibility
- **Dashboard Metric Calculation:** `DashboardService.java:123` queries `findByStoreIdAndStatus(storeId, "ACTIVE")`. Deactivating a product immediately removes it from active inventory counts.
- **Hard Deletion Breakdown:** Deleting a product permanently drops it from catalog metrics.

---

## 9. PRODUCT TEST COVERAGE (AUD-04-07)

### 9.1 Existing Tests in `ProductControllerTest.java`

| Test Method Name | What It Asserts | Execution Engine | Scope & Realism | Coverage Evaluation |
| :--- | :--- | :--- | :--- | :--- |
| `shouldRejectUnauthenticatedRequests` | `GET` and `POST` return 401 without Bearer token. | MockMvc + SpringBootTest (`grocery_db_test`) | Integration | **Genuinely covers** unauthenticated gatekeeper. |
| `shouldIsolateProductsBetweenStoresOnRead` | Store A product visible to Store A, hidden from Store B list and `GET /{id}`. | MockMvc + SpringBootTest (`grocery_db_test`) | Integration | **Genuinely covers** multi-tenant read isolation. |
| `shouldBlockStoreBFromModifyingStoreAProduct` | Store B `PUT /{id}` returns 404 for Store A product. | MockMvc + SpringBootTest (`grocery_db_test`) | Integration | **Genuinely covers** multi-tenant update isolation. |
| `shouldBlockStoreBFromDeletingStoreAProduct` | Store B `DELETE /{id}` returns 404 for Store A product. | MockMvc + SpringBootTest (`grocery_db_test`) | Integration | **Genuinely covers** multi-tenant delete isolation. |
| `shouldAllowSameSkuInDifferentStoresButRejectWithinSameStore` | Duplicate SKU in same store rejected (400); same SKU in different store allowed (200). | MockMvc + SpringBootTest (`grocery_db_test`) | Integration | **Genuinely covers** compound index and same-store SKU check. |
| `shouldRejectInvalidNegativePrices` | `costPrice < 0` or `sellingPrice < 0` returns 400. | MockMvc + SpringBootTest (`grocery_db_test`) | Integration | **Genuinely covers** negative price checks. |

### 9.2 Critical Test Gaps
The current test suite (`ProductControllerTest.java`) consists of 6 tests. The following areas are completely untested:
1. Product creation with client-supplied `id` (cross-tenant overwrite / ID hijacking).
2. Product creation with missing/blank required fields (`name`, `unit`).
3. Product creation with negative or zero initial stock.
4. Partial updates and field erasure verification on `PUT`.
5. SKU case-sensitivity collision (`SKU-1` vs `sku-1`).
6. Barcode duplication within the same store.
7. Product status toggle (`PATCH /{id}/status`) RBAC enforcement (Cashier access attempt).
8. Product search endpoint (`/search`) special characters and injection.
9. Combined category and status filtering (`?category=...&status=...`).
10. Hard deletion of a product referenced by sales/returns/inventory batches.
11. Inactive category assignment rejection.
12. Concurrent creation / update race conditions.

---

## 10. PERFORMANCE & MAINTAINABILITY (AUD-04-08)

1. **Unbounded In-Memory Listing:** `GET /api/v1/products` returns the entire product collection for a store. For catalogs with 20,000+ SKUs, this causes excessive memory allocation, garbage collection pauses, and large JSON payloads.
2. **Regex Query Performance:** `searchByStoreAndQuery` uses `$regex` with `$options: 'i'` across `name`, `sku`, and `barcode`. B-tree indexes cannot index case-insensitive unanchored regexes, forcing MongoDB into full collection scans.
3. **Absence of Service Layer:** All business logic, repository interactions, and validation are tightly coupled inside `ProductController.java` (239 lines).
4. **Entity Over-Exposure:** Exposing domain entity `Product` directly on controller methods prevents schema evolution, violates encapsulation, and introduces mass-assignment vulnerabilities.

---

## 11. ENVIRONMENT & TEST VERIFICATION

- **Database Verification:** `backend/src/test/resources/application.properties` specifies `spring.data.mongodb.uri=mongodb://127.0.0.1:27017/grocery_db_test`. Existing test reports (`backend/target/surefire-reports/TEST-com.grocery.ProductControllerTest.xml`) verify that all 6 tests in `ProductControllerTest` passed in 1.73 seconds.
- **Database Isolation:** Production database `grocery_db` remained untouched throughout the audit.
- **Verification Limitation:** Direct sandboxed execution of `mvn test` in the current terminal session was blocked by local file locking permissions on `.m2` repository locks; however, official Surefire test output generated on 2026-11-26 10:26 confirmed 6/6 test passes for `ProductControllerTest`.

---

## 12. COMPREHENSIVE FINDINGS LIST (GROUPED BY SEVERITY)

### 12.1 CRITICAL FINDINGS

#### FINDING AUD-04-CRIT-01: Client-Supplied ID Allows Cross-Tenant Product Hijacking and Overwrite
- **Severity:** CRITICAL
- **File / Method:** `backend/src/main/java/com/grocery/controller/ProductController.java` -> `createProduct(Product, UserPrincipal)`
- **Evidence:** Lines 100-141. The controller receives `@RequestBody Product product`. While it sets `product.setStoreId(storeId)`, it never invokes `product.setId(null)` or validates that `product.getId() == null`.
- **Root Cause:** Spring Data MongoDB's `save()` method performs an update/replace operation when an ID is present on the entity.
- **Impact:** An authenticated user in Store B can send a `POST /api/v1/products` request containing the `id` of an existing product belonging to Store A. MongoDB overwrites Store A's document, changing its `storeId` to Store B and replacing its fields, hijacking the product.
- **Reproduction Scenario:** Store A creates a product (ID `prod_A_101`). Store B sends `POST /api/v1/products` with payload `{"id": "prod_A_101", "name": "Hijacked", "sku": "NEW-SKU-B", "costPrice": 10, "sellingPrice": 20}`. Store A's product document is overwritten and transferred to Store B.
- **Recommended Solution:** Explicitly call `product.setId(null);` before saving in `createProduct`, or introduce a dedicated `ProductCreateRequest` DTO that lacks an `id` property.
- **Confidence:** Confirmed by source code inspection.

#### FINDING AUD-04-CRIT-02: Hard Deletion Breaks Referential Integrity for Historical Sales, Purchases, and Batches
- **Severity:** CRITICAL
- **File / Method:** `backend/src/main/java/com/grocery/controller/ProductController.java` -> `deleteProduct(String, UserPrincipal)`
- **Evidence:** Lines 234-237: `productRepository.findByStoreIdAndId(user.getStoreId(), id).map(product -> { productRepository.delete(product); ... });`.
- **Root Cause:** Physical deletion without referential integrity verification or soft deletion mechanism.
- **Impact:**
  - `SaleController.java:431`: Return processing looks up product by ID to restock items; returns on deleted products silently fail to restock.
  - `PurchaseController.java:127`: Receiving purchase orders looks up product by ID; receiving orders with deleted products fails to increment inventory.
  - `InventoryBatch` documents are orphaned.
  - `TenantMigrationService.java:114` fails to verify tenant boundaries for historical items.
- **Reproduction Scenario:** Complete a sale for product `P1`. Delete product `P1` via `DELETE /api/v1/products/{id}`. Process a customer return with restock requested. The product lookup returns null and restocked stock is lost.
- **Recommended Solution:** Replace hard deletion with soft deletion (`status = "ARCHIVED"` or boolean `deleted = true`), or enforce referential integrity checks rejecting deletion if historical sales/purchases/batches exist.
- **Confidence:** Confirmed by source code inspection.

---

### 12.2 HIGH FINDINGS

#### FINDING AUD-04-HIGH-01: Barcode Uniqueness Is Not Enforced, Leading to POS Scanner Failures
- **Severity:** HIGH
- **File / Method:** `backend/src/main/java/com/grocery/model/Product.java` (Line 26) & `ProductController.java` (Lines 128-130, 175-177)
- **Evidence:** `Product.java` declares `@Indexed private String barcode;` (non-unique). `ProductController` trims barcodes but never invokes `findByStoreIdAndBarcode`.
- **Root Cause:** Lack of compound unique index and application-level uniqueness validation for barcodes per store.
- **Impact:** Two products in the same store can share the same barcode. When POS scans the barcode, `ProductRepository.findByStoreIdAndBarcode` throws `IncorrectResultSizeDataAccessException` (expected 1, got 2), returning 500 Server Error and halting checkout.
- **Reproduction Scenario:** Create Product 1 with barcode `"89012345"`. Create Product 2 with barcode `"89012345"`. Both creations succeed. Query `GET /api/v1/pos/scan?barcode=89012345` (or repository lookup). System throws exception.
- **Recommended Solution:** Add compound unique index on `{ 'storeId': 1, 'barcode': 1 }` with `sparse = true`, and check `existsByStoreIdAndBarcode` on create/update.
- **Confidence:** Confirmed by source code inspection.

#### FINDING AUD-04-HIGH-02: Missing `@PreAuthorize` on Status Patch Endpoint Allows Unauthorized Status Manipulation by Cashiers
- **Severity:** HIGH
- **File / Method:** `backend/src/main/java/com/grocery/controller/ProductController.java` -> `updateStatus(String, Map<String, String>, UserPrincipal)`
- **Evidence:** Lines 203-221. `updateStatus` has `@PatchMapping("/{id}/status")` but lacks `@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")`.
- **Root Cause:** Omission of method security annotation.
- **Impact:** Cashiers (`CASHIER` role) can toggle any product's status between `ACTIVE` and `INACTIVE`, disrupting POS operations.
- **Reproduction Scenario:** Authenticate with `CASHIER` credentials. Send `PATCH /api/v1/products/{id}/status` with `{"status": "INACTIVE"}`. The request succeeds with 200 OK.
- **Recommended Solution:** Add `@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")` to `updateStatus`.
- **Confidence:** Confirmed by source code inspection.

#### FINDING AUD-04-HIGH-03: Direct Entity Binding on Update Causes Silent Data Erasure on Partial Payloads
- **Severity:** HIGH
- **File / Method:** `backend/src/main/java/com/grocery/controller/ProductController.java` -> `updateProduct(String, Product, UserPrincipal)`
- **Evidence:** Lines 174-196. The method copies fields directly from `update` to `existing`. Primitive double fields default to `0.0` when omitted in JSON.
- **Root Cause:** Absence of a DTO layer and lack of partial-update support.
- **Impact:** Sending an update payload omitting `costPrice` or `sellingPrice` silently sets those prices to `0.0`. Omitting `unit`, `description`, or `categoryId` sets them to `null`.
- **Reproduction Scenario:** Send `PUT /api/v1/products/{id}` with `{"name": "New Name"}`. Verify persisted product: `costPrice` is 0.0, `sellingPrice` is 0.0, `unit` is null.
- **Recommended Solution:** Create a `ProductUpdateRequest` DTO with boxed types (`Double`) and update existing fields only when explicitly provided and non-null.
- **Confidence:** Confirmed by source code inspection.

#### FINDING AUD-04-HIGH-04: Regex Injection and ReDoS Vulnerability in Product Search Query
- **Severity:** HIGH
- **File / Method:** `backend/src/main/java/com/grocery/repository/ProductRepository.java` -> `searchByStoreAndQuery(String, String)`
- **Evidence:** Line 34: `@Query("{'storeId': ?0, '$or': [{'name': {'$regex': ?1, '$options': 'i'}}, {'sku': {'$regex': ?1, '$options': 'i'}}, {'barcode': {'$regex': ?1, '$options': 'i'}}]}")`.
- **Root Cause:** Unsanitized user search string injected directly into MongoDB `$regex`.
- **Impact:** Searching for unescaped regex characters (such as `(`, `[`, or `*`) causes MongoDB pattern syntax errors and 500 responses. Submitting catastrophic backtracking regexes causes high CPU load (ReDoS).
- **Reproduction Scenario:** Send `GET /api/v1/products/search?q=(abc` with valid auth token. MongoDB driver throws `PatternSyntaxException` or server returns 500 error.
- **Recommended Solution:** Escape special regex characters via `java.util.regex.Pattern.quote(query)` before executing query, or utilize MongoDB text indexes.
- **Confidence:** Confirmed by source code inspection.

#### FINDING AUD-04-HIGH-05: Direct Stock Assignment Bypasses Inventory Audit Ledger, While Stock Edits Are Silently Discarded
- **Severity:** HIGH
- **File / Method:** `backend/src/main/java/com/grocery/controller/ProductController.java` (Lines 141, 159-200) & `frontend/products.html` (Lines 213, 441)
- **Evidence:** In `ProductController.java`, `createProduct` saves `currentStock` without creating an `InventoryTransaction`. In `updateProduct`, `existing.setCurrentStock(...)` is not called, but `frontend/products.html` provides a stock input in the edit modal and submits it.
- **Root Cause:** Inconsistent inventory architecture separating creation from updates without communicating restrictions to frontend.
- **Impact:** Initial stock has no audit record. Users editing stock via `products.html` receive a success message, but stock values remain unchanged on the server.
- **Reproduction Scenario:** Open Edit Modal on a product with stock 10. Change stock to 50. Click Save. Modal closes with "Product updated successfully". Refresh page: stock is still 10.
- **Recommended Solution:** Remove stock input from edit modal (redirect to inventory adjustments). On product creation, trigger an `InventoryTransaction` of type `INITIAL_STOCK`.
- **Confidence:** Confirmed by source code inspection.

---

### 12.3 MEDIUM FINDINGS

#### FINDING AUD-04-MED-01: Incomplete Server-Side Validation on Core Product Fields
- **Severity:** MEDIUM
- **File / Method:** `backend/src/main/java/com/grocery/controller/ProductController.java` (Lines 109-138) & `Product.java`
- **Evidence:** `Product.java` lacks `@NotBlank`, `@Size`, `@Min`, `@Pattern` annotations. `ProductController` does not validate `name`, `unit`, `minStockLevel`, `taxRate`, or `status`.
- **Root Cause:** Absence of Bean Validation (`jakarta.validation.Valid`).
- **Impact:** Products with blank names, invalid units, or negative alert thresholds can be persisted via API.
- **Confidence:** Confirmed by source code inspection.

#### FINDING AUD-04-MED-02: Mutually Exclusive Filter Blocks Ignore Status When Category Is Provided
- **Severity:** MEDIUM
- **File / Method:** `backend/src/main/java/com/grocery/controller/ProductController.java` -> `getProducts(String, String, UserPrincipal)`
- **Evidence:** Lines 52-57. If `category` is provided, the method returns `findByStoreIdAndCategoryId(storeId, category)`. The subsequent `status` check is skipped.
- **Root Cause:** Cascading if-else structure without composite querying.
- **Impact:** Queries combining category and status (e.g. `?category=CAT-1&status=ACTIVE`) return inactive products within that category.
- **Confidence:** Confirmed by source code inspection.

#### FINDING AUD-04-MED-03: Lack of Server-Side Pagination on Product Listing
- **Severity:** MEDIUM
- **File / Method:** `backend/src/main/java/com/grocery/controller/ProductController.java` (`getProducts`)
- **Evidence:** Lines 42-59 return unbounded `List<Product>`.
- **Root Cause:** Absence of `Pageable` parameters.
- **Impact:** Severe memory and network overhead when catalog size scales to thousands of items.
- **Confidence:** Confirmed by source code inspection.

#### FINDING AUD-04-MED-04: Inconsistent API Response Enveloping Across Endpoints
- **Severity:** MEDIUM
- **File / Method:** `backend/src/main/java/com/grocery/controller/ProductController.java`
- **Evidence:** `GET /products`, `GET /{id}`, `GET /search`, and `GET /check-sku` return raw data/lists/maps. `POST`, `PUT`, `PATCH`, and `DELETE` return `ApiResponse<T>`.
- **Root Cause:** Lack of standardized response wrapper enforcement.
- **Impact:** Increased frontend complexity and higher probability of parsing errors.
- **Confidence:** Confirmed by source code inspection.

#### FINDING AUD-04-MED-05: Denormalized Category Name Becomes Stale on Category Update
- **Severity:** MEDIUM
- **File / Method:** `backend/src/main/java/com/grocery/controller/CategoryController.java` -> `updateCategory(...)`
- **Evidence:** `CategoryController.java:61-70`. Renaming a category does not update `categoryName` on existing `Product` records.
- **Root Cause:** Unsynchronized denormalization.
- **Impact:** Products retain obsolete category names until individually re-saved.
- **Confidence:** Confirmed by source code inspection.

#### FINDING AUD-04-MED-06: Use of Primitive `double` for Financial Currency Fields
- **Severity:** MEDIUM
- **File / Method:** `backend/src/main/java/com/grocery/model/Product.java` (Lines 36-37)
- **Evidence:** `private double costPrice; private double sellingPrice;`.
- **Root Cause:** Inappropriate numerical type for monetary precision.
- **Impact:** Floating-point rounding inaccuracies in financial reporting, margins, and taxes.
- **Confidence:** Confirmed by source code inspection.

---

### 12.4 LOW FINDINGS

#### FINDING AUD-04-LOW-01: Redundant Indexes on Single-Field SKU and StoreId
- **Severity:** LOW
- **File / Method:** `backend/src/main/java/com/grocery/model/Product.java` (Lines 22, 29)
- **Evidence:** `@Indexed private String sku;` and `@Indexed private String storeId;` coexist with `@CompoundIndex(name = "store_sku_unique_idx", def = "{'storeId': 1, 'sku': 1}", unique = true)`.
- **Root Cause:** Redundant index declarations.
- **Impact:** Additional memory and write overhead on MongoDB index maintenance.
- **Confidence:** Confirmed by source code inspection.

#### FINDING AUD-04-LOW-02: Residual Non-Tenant-Scoped Methods in `ProductRepository`
- **Severity:** LOW
- **File / Method:** `backend/src/main/java/com/grocery/repository/ProductRepository.java` (Lines 13-22)
- **Evidence:** `findBySku`, `findByBarcode`, `findByCategoryId`, `findByStatus`, `searchProducts`, `findLowStockProducts`.
- **Root Cause:** Legacy methods remaining from pre-multitenancy architecture.
- **Impact:** Risk of developer misuse in future feature development.
- **Confidence:** Confirmed by source code inspection.

#### FINDING AUD-04-LOW-03: Complete Absence of Product Image Management Architecture
- **Severity:** LOW
- **File / Method:** `backend/src/main/java/com/grocery/model/Product.java` & `frontend/products.html`
- **Evidence:** `Product.java` lacks image URL/path fields; no upload endpoint or storage exists.
- **Root Cause:** Feature not implemented.
- **Impact:** Visual identification unavailable in POS or catalog.
- **Confidence:** Confirmed by source code inspection.

#### FINDING AUD-04-LOW-04: Absence of Optimistic Locking (`@Version`)
- **Severity:** LOW
- **File / Method:** `backend/src/main/java/com/grocery/model/Product.java`
- **Evidence:** No `@Version private Long version;` present on the model.
- **Root Cause:** Omission of concurrency controls.
- **Impact:** Simultaneous updates overwrite each other without warning.
- **Confidence:** Confirmed by source code inspection.

---

## 13. RECOMMENDED REMEDIATION PRIORITY

Remediation should proceed strictly in phased priority order:

```
+-----------------------------------------------------------------------------------+
| PHASE 1: IMMEDIATE CRITICAL FIXES (Security & Data Corruption)                    |
| - Sanitize ID on product creation (setId(null)) to prevent tenant hijacking.      |
| - Introduce soft deletion (status = 'ARCHIVED') and block hard deletion.          |
| - Add @PreAuthorize to PATCH /{id}/status.                                        |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| PHASE 2: INTEGRITY & SCANNER RELIABILITY                                          |
| - Enforce store-scoped barcode uniqueness (Compound sparse unique index).         |
| - Quote search query input in ProductRepository to eliminate ReDoS.               |
| - Establish Product DTO layer (CreateRequest, UpdateRequest, Response).           |
| - Prevent partial update field erasure.                                           |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| PHASE 3: AUDIT TRAIL & BUSINESS VALIDATION                                        |
| - Log InventoryTransaction on initial stock during creation.                      |
| - Remove confusing stock input from edit modal in products.html.                  |
| - Add Jakarta validation (@NotBlank on name, unit, etc.).                         |
| - Support composite filtering (category AND status).                              |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| PHASE 4: PERFORMANCE & TEST HARDENING                                             |
| - Implement server-side pagination with Pageable.                                 |
| - Remove redundant single-field indexes in Product.java.                          |
| - Clean up dead repository methods.                                               |
| - Expand ProductControllerTest coverage from 6 tests to 18+ comprehensive tests.  |
+-----------------------------------------------------------------------------------+
```

---

## 14. AUDIT LIMITATIONS

1. **Read-Only Constraint:** Per audit rules, no application code, tests, or configurations were modified.
2. **Terminal Sandbox Lock on `.m2`:** Direct execution of `mvn test` in the current terminal sandbox encountered a file channel lock exception on the local `.m2` repository; however, verification was successfully achieved by inspecting the latest Surefire test reports (`TEST-com.grocery.ProductControllerTest.xml`), confirming all 6 existing tests passed.
3. **External Dependencies:** Image file storage and cloud hosting integrations were not evaluated as no image management code exists.

---

## 15. CONCLUSION

The Products module contains functioning baseline multi-tenant scoping and compound SKU uniqueness, but possesses **severe architectural and security vulnerabilities**:
- **Cross-tenant ID hijacking** (AUD-04-CRIT-01) and **destructive hard deletion** (AUD-04-CRIT-02) threaten tenant boundaries and historical audit integrity.
- **Missing barcode uniqueness** (AUD-04-HIGH-01) and **missing status authorization** (AUD-04-HIGH-02) expose store operations to POS crashes and privilege escalation.
- **Direct entity binding** (AUD-04-HIGH-03) and **untracked stock edits** (AUD-04-HIGH-05) lead to silent data corruption.

Stage 1 is complete. No fixes have been implemented. Remediation must await approval of this evidence audit report.
