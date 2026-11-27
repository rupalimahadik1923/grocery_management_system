# PRODUCT CREATION ADVERSARIAL HARDENING & BRUTAL TEST REPORT

**Project:** Grocery Management System  
**Workspace:** `C:\Users\Siddheshwar\Desktop\grocery\grocery_management_system`  
**Target:** `POST /api/v1/products` (Product Creation)  
**Execution Date:** 2026-11-26  
**Environment:** `grocery_db_test` (Isolated Standalone MongoDB v9.0.8 on `127.0.0.1:27017`)  
**Production Database:** `grocery_db` — STRICTLY UNTOUCHED  
**Test Classes Executed:**
1. `com.grocery.ProductCreationBrutalTest` (23 adversarial tests)
2. `com.grocery.ProductControllerTest` (26 regression tests)

---

## 1. EXECUTIVE SUMMARY

In strict compliance with the Senior Architecture Review directives, the "Add Product" workflow (`POST /api/v1/products`) was hardened and subjected to an exhaustive battery of adversarial tests covering:
- Role-based authorization & unauthenticated access.
- Tenant context spoofing & cross-tenant data isolation.
- Existing document ID hijacking and overwrite protection.
- Null, missing, empty, control-character, and boundary-overflow validation.
- Store-scoped category verification, inactive category rejection, and server-authoritative `categoryName` derivation.
- Canonical SKU normalization (`trim().toUpperCase()`) and multi-tenant sharing.
- Safe XSS / NoSQL payload persistence without script execution.
- Real multi-threaded database concurrency races (10 parallel threads competing for the exact same canonical SKU).

### Key Test Results:
- **`ProductCreationBrutalTest`:** **23 tests run, 0 failures, 0 errors, 0 skipped** (Execution time: 13.31 s).
- **`ProductControllerTest`:** **26 tests run, 0 failures, 0 errors, 0 skipped** (Execution time: 20.02 s).
- **Total Tests Passed:** **49 tests** across product creation, update, and lifecycle suites.

---

## 2. BASELINE DEFECTS CONFIRMED & BACKEND HARDENING CHANGES

Prior to this phase, `ProductController.createProduct` accepted direct binding to `Product` entity and lacked several critical boundary guards. The following changes were implemented in `ProductController.java`:

### 1. Introduction of `ProductCreateRequest` DTO
- Created [ProductCreateRequest.java](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/dto/ProductCreateRequest.java) with boxed wrapper types.
- Client cannot supply protected fields such as `id`, `storeId`, or internal audit metadata.
- `ProductController.createProduct` now unmarshalls from `ProductCreateRequest` and constructs a persistent `Product` entity with server-controlled properties (`product.setId(null)`, `product.setStoreId(storeId)`).

### 2. Product Name & String Boundaries
- **Required & Trimmed:** Rejects null, empty, or whitespace-only names with HTTP 400 (`"Product name is required"`).
- **Length Limit:** Enforces `MAX_NAME_LENGTH = 150` (`"Product name cannot exceed 150 characters"`).
- **Control Characters:** Enforces `containsControlCharacters(...)`, strictly rejecting null bytes (`\0`) and non-printable control characters.

### 3. SKU Character Limits & Concurrency Protection
- **Uppercase & Trimmed:** Canonical normalization (`cleanSku = req.getSku().trim().toUpperCase()`).
- **Length & Control Characters:** Maximum 50 characters; rejects null bytes.
- **Race Condition Handler:** Wrapped `productRepository.save(product)` in `try-catch (DuplicateKeyException ex)` returning HTTP 400 (`"SKU already exists in this store"`), preventing leaked 500 stack traces when concurrent threads slip past the preliminary `existsByStoreIdAndSku` check.

### 4. Numeric Bounds & Sanity
- **Upper Bounds:** Enforces `MAX_PRICE_VALUE = 1,000,000.0` and `MAX_STOCK_VALUE = 1,000,000.0` preventing astronomical floating-point overflow.
- **Negative Rejection:** Strictly rejects negative cost price, selling price, and initial stock with HTTP 400.
- **Tax Rate:** Validated between $0.0\%$ and $100.0\%$.

### 5. Category Lifecycle & Server Derivation
- Category ID is resolved against `CategoryRepository` for authenticated `storeId`.
- Rejects non-existent categories (400), cross-store categories (400), and inactive categories (400).
- Malformed ObjectId strings are safely trapped without HTTP 500 crashes.
- Client-supplied `categoryName` is completely ignored; derived authoritatively from database record.

### 6. Status Rejection
- Explicit creation with `ARCHIVED` or `DELETED` is strictly rejected with HTTP 400 (`"New products cannot be created in ARCHIVED or DELETED status"`).
- Missing status defaults to `"ACTIVE"`. Invalid status strings are rejected with HTTP 400.

---

## 3. ADVERSARIAL TEST MATRIX & RESULTS

All 23 adversarial tests executed in `ProductCreationBrutalTest.java`:

| Test ID | Test Method | Adversarial Vector / Condition | Observed Result | Status |
|---|---|---|---|---|
| **BRUTAL-01** | `brutal01_unauthenticatedRequest` | Missing Authorization header | HTTP 401 Unauthorized | **PASS** |
| **BRUTAL-02** | `brutal02_cashierForbidden` | Valid JWT with `ROLE_CASHIER` | HTTP 403 Forbidden | **PASS** |
| **BRUTAL-03** | `brutal03_tenantSpoofing` | Store A user supplies `storeId: "store_b"` in body | Product persisted under Store A; Store B has 0 documents | **PASS** |
| **BRUTAL-04** | `brutal04_documentHijacking` | Attacker supplies ID of an existing Store B product | Server generates fresh ID; Store B victim doc untouched | **PASS** |
| **BRUTAL-05** | `brutal05_nameValidation` | `name: null` and `name: "   "` | HTTP 400 Bad Request ("Product name is required") | **PASS** |
| **BRUTAL-06** | `brutal06_missingSku` | `sku: null` and `sku: " \t "` | HTTP 400 Bad Request ("SKU is required") | **PASS** |
| **BRUTAL-07** | `brutal07_duplicateSkuSameStore` | Submitting `sku-dup-01` after `SKU-DUP-01` | HTTP 400 Bad Request ("SKU already exists in this store") | **PASS** |
| **BRUTAL-08** | `brutal08_sharedSkuCrossStore` | Store A and Store B create same SKU | Both succeed with HTTP 200; multi-tenant isolation intact | **PASS** |
| **BRUTAL-09** | `brutal09_negativeCostPrice` | `costPrice: -0.01` | HTTP 400 Bad Request ("cannot be negative") | **PASS** |
| **BRUTAL-10** | `brutal10_negativeSellingPrice`| `sellingPrice: -10.0` | HTTP 400 Bad Request ("cannot be negative") | **PASS** |
| **BRUTAL-11** | `brutal11_negativeStock` | `currentStock: -5.0` | HTTP 400 Bad Request ("Initial stock cannot be negative") | **PASS** |
| **BRUTAL-12** | `brutal12_extremePriceOverflow`| `sellingPrice: 5,000,000.0` (limit: 1,000,000) | HTTP 400 Bad Request ("exceeds maximum permitted limit") | **PASS** |
| **BRUTAL-13** | `brutal13_crossStoreCategory` | Store A user supplies Store B category ID | HTTP 400 Bad Request ("Category does not belong to your store") | **PASS** |
| **BRUTAL-14** | `brutal14_inactiveCategory` | Supplying category with `active: false` | HTTP 400 Bad Request ("Cannot assign an inactive category") | **PASS** |
| **BRUTAL-15** | `brutal15_nonExistentCategory` | Supplying non-existent MongoDB ObjectId | HTTP 400 Bad Request ("Category does not belong to your store") | **PASS** |
| **BRUTAL-16** | `brutal16_categoryNameForgery`| Sending valid `categoryId` with fake `categoryName` | DB category name saved; forged client name ignored | **PASS** |
| **BRUTAL-17** | `brutal17_whitespaceBarcode` | `barcode: "   \t  "` | Persisted as `null` in MongoDB | **PASS** |
| **BRUTAL-18** | `brutal18_statusTampering` | Explicit `status: "ARCHIVED"` or `"INVALID_JUNK"` | HTTP 400 Bad Request ("cannot be created in ARCHIVED or DELETED status") | **PASS** |
| **BRUTAL-19** | `brutal19_xssAndInjection` | `<script>alert('XSS')</script>`, `'; DROP TABLE;--` | Persisted safely as literal string; no code execution | **PASS** |
| **BRUTAL-20** | `brutal20_concurrentSameSkuRace` | **10 parallel threads** competing for same SKU | **Exactly 1 succeeds (200), 9 rejected (400), 1 DB doc** | **PASS** |
| **BRUTAL-22** | `brutal22_controlCharacters` | Null bytes (`\0`) in name or SKU | HTTP 400 Bad Request ("cannot contain control characters") | **PASS** |
| **BRUTAL-23** | `brutal23_massiveString` | `name: "A".repeat(500)` (limit: 150) | HTTP 400 Bad Request ("cannot exceed 150 characters") | **PASS** |
| **BRUTAL-31** | `brutal31_invalidToken` | Invalid signature Bearer token | HTTP 401 Unauthorized | **PASS** |

---

## 4. CONCURRENCY TEST METHODOLOGY & PERSISTED COUNTS (BRUTAL-20)

### Execution Setup
- **Concurrency Mechanism:** Java `ExecutorService` with 10 worker threads.
- **Barrier Synchronization:** `CountDownLatch startLatch = new CountDownLatch(1)`. All 10 threads constructed their payloads and blocked on `startLatch.await()`, releasing simultaneously to generate maximum write contention.
- **Payload:** All 10 threads posted the exact same SKU: `"SKU-RACE-" + UUID.randomUUID()`.
- **Target Database:** Real MongoDB instance (`grocery_db_test`), compound index `(storeId, sku)`.

### Persisted Verification:
- **HTTP 200 (Success):** `1`
- **HTTP 400 (Duplicate Rejection):** `9`
- **HTTP 500 (Internal Error):** `0`
- **Database Document Count Query:**
  ```java
  List<Product> matches = productRepository.findByStoreId(storeA.getId()).stream()
          .filter(prod -> raceSku.equalsIgnoreCase(prod.getSku()))
          .toList();
  assertEquals(1, matches.size());
  ```
  **Result:** Exactly **1** document was persisted in the collection for that store and canonical SKU.

---

## 5. DATABASE & INDEX VERIFICATION EVIDENCE

- **Verified Database URI:** `mongodb://127.0.0.1:27017/grocery_db_test` (from `backend/src/test/resources/application.properties`).
- **Production Safety:** Production database `grocery_db` was **never connected to or modified**.
- **Compound Unique Index:** `DataSeeder.java` verified compound unique index on startup:
  `store_sku_unique_idx (storeId, sku)`.
- **Duplicate Key Exception Handling:** Verified that MongoDB duplicate key error code 11000 is intercepted by `ProductController` and mapped to HTTP 400 Bad Request without leaking database stack traces.

---

## 6. EXPLICIT OPENING-STOCK STATEMENT

In accordance with strict audit rules:
- **Opening-stock ledger consistency remains outside the scope of this task**.
- Negative initial stock is rejected with HTTP 400.
- No unsafe standalone `InventoryTransaction` insertion was added after product save.
- Complete ledger consistency will be implemented under the separate Stage 3A.2 durable stock-movement architecture.

---

## 7. FINAL VERDICT

**ADVERSARIAL HARDENING & BRUTAL TEST SUITE VERIFIED COMPLETE**

The `POST /api/v1/products` endpoint has been successfully hardened against tenant spoofing, document hijacking, SKU collision races, malformed categories, extreme price values, control characters, and status tampering. All 49 automated tests (23 brutal + 26 regression) pass cleanly with zero failures.
