# MODULE 04 — PRODUCTS
# STAGE 3: STOCK INTEGRITY, BARCODE & UPDATE BEHAVIOR — READ-ONLY AUDIT REPORT

**Project:** Grocery Management System  
**Workspace:** `C:\Users\Siddheshwar\Desktop\grocery\grocery_management_system`  
**Audit Target:** Products Module — Stock Integrity, Barcode Uniqueness & Update Behavior  
**Stage:** 3 — Comprehensive Read-Only Audit & Remediation Plan  
**Target Environment:** `grocery_db_test` (Execution strictly read-only, non-destructive)  
**Date:** 2026-11-26  
**Auditor Roles:** Senior Software Architect, Spring Boot Backend Engineer, MongoDB Data Integrity Specialist, QA Engineer  

---

## 1. EXECUTIVE SUMMARY

Stage 3 conducted an exhaustive, read-only architectural and source-code investigation into three critical operational dimensions of the Products module:
1. **Stock Integrity & Inventory Ledger Consistency**: Tracing every stock modification pathway, `Product.currentStock` synchronization, and ledger (`InventoryTransaction`) creation across POS, Purchases, Returns, Adjustments, and Product creation/update.
2. **Barcode Integrity & Lookups**: Assessing barcode uniqueness, normalization, null/blank indexing risks, and cross-store/POS lookup ambiguities.
3. **Product Update Safety & Data Loss**: Analyzing entity binding in `ProductController.updateProduct`, frontend payload transmission in `products.html`, mutable vs. immutable historical fields, and stock overwrites.

### High-Level Audit Findings:
- **Major Stock Ledger Bypass**: When a new product is created (`POST /api/v1/products`), any client-supplied `currentStock` (e.g. 50 units) is directly persisted into `Product.currentStock` without generating an `InventoryTransaction` of type `OPENING_STOCK`. The inventory ledger starts empty while stock is non-zero.
- **Dangerous Full-Entity Overwrite on Update**: `PUT /api/v1/products/{id}` binds directly to the `Product` entity without a DTO. Although it doesn't update `currentStock` directly, omitting fields sets them to default Java values (e.g., primitives like `minStockLevel` defaulting to `0.0`, `taxRate` defaulting to `0.0`), and `products.html` transmits `currentStock` in the payload even though `PUT` silently ignores it, misleading administrators.
- **Missing Barcode Uniqueness & High Collisions Risk**: Neither MongoDB nor the application enforces barcode uniqueness. In `Product.java`, only a compound unique index exists for `(storeId, sku)`. `@Indexed private String barcode;` creates a non-unique global index. Multiple products within the same store can share the exact same barcode. In `pos.html` and `handleBarcodeInput`, `products.find()` simply selects the first match, risking cashier scanning errors and billing the wrong item.
- **Read-Modify-Write Concurrency Races on Stock**: All stock modifications in `SaleController`, `PurchaseController`, and `InventoryController` follow a non-atomic `find -> set -> save` pattern. Under concurrent checkouts on the same SKU, lost updates occur.

**Stage 3 Final Audit Verdict: AUDIT COMPLETE — REMEDIATION PLAN APPROVED FOR REVIEW**

---

## 2. AUDIT SCOPE

The audit was conducted strictly in read-only mode across:
- **Backend**:
  - `ProductController.java`
  - `SaleController.java`
  - `PurchaseController.java`
  - `InventoryController.java`
  - `Product.java`, `InventoryTransaction.java`, `InventoryBatch.java`
  - `ProductRepository.java`, `InventoryTransactionRepository.java`
  - `DataSeeder.java`
  - `GlobalExceptionHandler.java`
- **Frontend**:
  - `frontend/products.html`
  - `frontend/pos.html`
  - `frontend/inventory.html`
- **Test Suites**:
  - `backend/src/test/java/com/grocery/ProductControllerTest.java` (21 tests)
  - Application-wide test suites (57 tests)

---

## 3. WORKSPACE AND GIT STATUS CONFIRMATION

- **Current Working Directory:** `c:\Users\Siddheshwar\Desktop\grocery\grocery_management_system`
- **Branch:** `main` (up to date with `origin/main`)
- **Pre-existing Working Tree State:**
  - `frontend/pos.html`: Remediated in Stage 2.2 (active product filtering).
  - `backend/src/test/java/com/grocery/ProductControllerTest.java`: Updated in Stage 2 with 21 passing integration tests.
  - Core domain models and controllers contain accepted Stage 2 P0 remediations and previous dashboard hardening.
  - Working tree has zero staged changes; no files were modified or deleted during this Stage 3 audit.

---

## 4. STOCK MUTATION PATHWAY MAP

| Pathway | API Endpoint | Business Purpose | Modifies `Product.currentStock`? | Creates `InventoryTransaction`? | Transaction Timing | Atomicity / Concurrency Mechanism | Idempotent? | Tenant Scoped? |
|---|---|---|---|---|---|---|---|---|
| **Product Creation** | `POST /api/v1/products` | New product catalog onboarding | **YES** (persists whatever `currentStock` is supplied) | **NO** (Bypasses ledger completely) | None | Standard Mongo `save` | No | Yes (`storeId` enforced) |
| **Product Edit** | `PUT /api/v1/products/{id}` | Edit product metadata | **NO** (ignores `update.getCurrentStock()`) | **NO** | None | Entity overwrite | Yes | Yes (`storeId` enforced) |
| **POS Checkout** | `POST /api/v1/sales` | Cashier retail sale | **YES** (`Math.max(0, currentStock - qty)`) | **YES** (`type: "SALE"`, `-qty`) | After product save | Non-atomic Read-Modify-Write (`find -> save`) | No (Creates duplicate sale) | Yes (`storeId` enforced) |
| **Customer Return** | `POST /api/v1/sales/returns` | Process product return | **YES** (`currentStock + qty` if restocked) | **YES** (`type: "CUSTOMER_RETURN"`, `+qty`) | After product save | Non-atomic Read-Modify-Write | Yes (via `reservationMetadata` & `restockProcessed`) | Yes (`storeId` enforced) |
| **PO Goods Receiving** | `POST /api/v1/purchases/{id}/receive` | Inward supplier shipment | **YES** (`currentStock + orderQty`) | **YES** (`type: "PURCHASE_RECEIPT"`, `+orderQty`) | After product save | Non-atomic Read-Modify-Write | Guarded by `po.status == "RECEIVED"` | Yes (`storeId` enforced) |
| **Manual Stock Adjustment** | `POST /api/v1/inventory/adjustments` | Physical count / audit adjustment | **YES** (`Math.max(0, currentStock + qty)`) | **YES** (`type: adjustmentType`, `qty`) | After product save | Non-atomic Read-Modify-Write | No | Yes (`storeId` enforced) |
| **DataSeeder** | Application Startup | Initial seed starter products | **YES** (hardcoded initial values) | **NO** | None | Standalone startup `save` | Guarded by `count() == 0` | Yes (`defaultStore`) |

---

## 5. PRODUCT STOCK INTEGRITY ANALYSIS

### 5.1 Mutable vs. Calculated Stock Source
`Product.currentStock` is stored as a mutable `double` field in the `products` MongoDB collection. It is **not** a dynamically aggregated projection from `inventory_transactions`.
- **Integrity Risk:** Any direct update to `Product.currentStock` that fails to record an `InventoryTransaction` creates an unexplainable ledger discrepancy.
- **Initial Stock Leak:** `ProductController.createProduct` (lines 142–165) accepts an initial `currentStock` from the client and persists it, but writes 0 ledger entries. Therefore, `Product.currentStock` has initial stock, but querying `/api/v1/inventory/transactions` shows zero historical movements.

### 5.2 Negative Stock Prevention
- In `SaleController.java` (line 132): `double newStock = Math.max(0, product.getCurrentStock() - item.getQuantity());`
- In `InventoryController.java` (line 99): `double newStock = Math.max(0, oldStock + quantity);`
- **Defect:** Clamping to `0` using `Math.max(0, ...)` prevents negative numbers in the database, but **silently masks inventory deficits**. If a store has 2 units in stock and a cashier sells 5 units, `newStock` is set to `0`, recording `-5` in the transaction ledger while only deducting `2` from the physical balance. There is no pre-sale stock availability check or configurable backorder prevention.

---

## 6. INVENTORY TRANSACTION INTEGRITY ANALYSIS

### 6.1 Transaction Schema & Representation
- **Model:** `com.grocery.model.InventoryTransaction`
- **Fields:** `productId`, `productName`, `type`, `quantity`, `balanceAfter`, `referenceType`, `referenceId`, `performedBy`, `notes`, `storeId`, `createdAt`.
- **Types in Use:** `SALE`, `CUSTOMER_RETURN`, `PURCHASE_RECEIPT`, `ADJUSTMENT`.
- **Missing Types:** `OPENING_STOCK` (never emitted), `DAMAGE` (not distinguished in API), `EXPIRY` (no automated expiration stock write-off).

### 6.2 Partial Failure and Rollback Gap
Across all stock modification endpoints (`SaleController`, `PurchaseController`, `InventoryController`):
1. `productRepository.save(product)` is executed first.
2. `transactionRepository.save(tx)` is executed second.
- **Risk:** The application runs against standalone MongoDB without multi-document transactional boundaries (`@Transactional` with replica set session). If the transaction repository write fails (e.g., validation, crash, network disconnect), the product's `currentStock` remains permanently altered while no ledger entry exists.

---

## 7. STOCK CONCURRENCY & RACE CONDITIONS

### 7.1 Read-Modify-Write Concurrency
None of the stock mutation pathways use MongoDB atomic operators (e.g., `$inc` with optimistic locking `findAndModify` or query criteria `currentStock >= qty`).
- **Reproduction Scenario:**
  - Product X currently has `currentStock = 10`.
  - Cashier 1 sells 2 units of Product X at `T0`. Thread reads `currentStock = 10`.
  - Cashier 2 sells 3 units of Product X at `T0 + 1ms`. Thread reads `currentStock = 10`.
  - Cashier 1 calculates `newStock = 10 - 2 = 8` and saves `currentStock = 8`.
  - Cashier 2 calculates `newStock = 10 - 3 = 7` and saves `currentStock = 7`.
  - **Result:** Actual sales = 5 units. Final stock in DB = 7 units (should be 5). 2 units of stock loss vanish from the database.

---

## 8. BARCODE VALIDATION & INTEGRITY ANALYSIS

### 8.1 Validation Deficiencies
Inspect `ProductController.java` (lines 151-153):
```java
if (product.getBarcode() != null) {
    product.setBarcode(product.getBarcode().trim());
}
```
- **No Uniqueness Enforcement:** Neither `createProduct` nor `updateProduct` checks `productRepository.existsByStoreIdAndBarcode`.
- **No Blank/Empty Normalization:** If a client passes `""` (empty string) or whitespace `"   "`, it is stored as `""`.
- **Optional Barcodes:** Barcodes are optional, but empty strings versus `null` values are not normalized.

### 8.2 Barcode MongoDB Index Analysis
Inspect `Product.java` (lines 12–14, 25–26) and `DataSeeder.java` (lines 208–213):
```java
@CompoundIndexes({
    @CompoundIndex(name = "store_sku_unique_idx", def = "{'storeId': 1, 'sku': 1}", unique = true)
})
...
@Indexed
private String barcode;
```
- **Finding:** The only compound unique index is `(storeId, sku)`.
- The index on `barcode` is a standard non-unique global index.
- **Risk of Indexing Empty Strings:** If a compound unique index `{'storeId': 1, 'barcode': 1}` were naively applied without a partial filter (`$type: "string", $gt: ""`), all products with null or blank barcodes would trigger duplicate key errors (`E11000 duplicate key error`).

### 8.3 Barcode Lookup Ambiguity in POS & Search
In `pos.html` (`handleBarcodeInput`):
```javascript
let match = products.find(p => 
  p.status === 'ACTIVE' && (
    (p.barcode && p.barcode.toLowerCase() === lower) ||
    (p.sku && p.sku.toLowerCase() === lower)
  )
);
```
- If two different products have identical barcodes (e.g. accidentally assigned duplicate barcodes), `products.find()` non-deterministically resolves to whichever product appears first in array order. The cashier will bill the wrong product.

---

## 9. PRODUCT UPDATE BEHAVIOR & UNINTENDED DATA LOSS

### 9.1 Direct Entity Binding Vulnerabilities (`PUT /api/v1/products/{id}`)
`ProductController.updateProduct` (lines 170-224) binds directly to `@RequestBody Product update`:
```java
existing.setName(update.getName());
...
existing.setUnit(update.getUnit());
existing.setCostPrice(update.getCostPrice());
existing.setSellingPrice(update.getSellingPrice());
existing.setMinStockLevel(update.getMinStockLevel());
existing.setTaxRate(update.getTaxRate());
existing.setDescription(update.getDescription());
existing.setStatus(update.getStatus());
```
- **Data Loss on Partial Updates:** If an API client sends a JSON payload omitting `taxRate` or `minStockLevel`, Jackson defaults primitive double fields to `0.0`. The existing `minStockLevel` (e.g., 10.0) is overwritten with `0.0`.
- **Status Hijacking on Update:** Although `PATCH /api/v1/products/{id}/status` was secured with whitelist validation in Stage 2, `PUT /api/v1/products/{id}` directly executes `existing.setStatus(update.getStatus())` with **zero validation against `ALLOWED_STATUSES`**, allowing arbitrary statuses or un-archival via `PUT`.

### 9.2 Frontend Disconnect in `frontend/products.html`
In `products.html` (lines 404–446):
- The edit modal renders a field `document.getElementById('prod-stock').value = prod.currentStock`.
- On submit, the payload includes `currentStock: parseFloat(...)`.
- However, `ProductController.updateProduct` deliberately ignores `update.getCurrentStock()`.
- **Architectural Confusion:** A user editing a product in `products.html` believes they are editing stock, but the change is silently dropped by the backend with no error or notification. Stock adjustments must be routed through `/api/v1/inventory/adjustments`.

---

## 10. HISTORICAL COMPATIBILITY & IMMUTABILITY

### 10.1 Mutable vs. Immutable Product Attributes
When a product's price, tax rate, or name is updated:
- `Sale.java` takes a server-authoritative snapshot of `costPrice`, `sellingPrice`, `unitPrice`, and `taxRate` at checkout. Historical sales are preserved.
- `PurchaseOrder.java` snapshots `unitCost` in `PurchaseOrderItem`.
- **Integrity Defect:** If a product's `name` or `sku` is updated, historical sales, purchase orders, and inventory transactions that reference the product only by `productId` or store denormalized names reflect historical data, but searching by new SKU may cause audit trail disconnects if SKU changes are unmonitored.

---

## 11. TEST COVERAGE ANALYSIS

### 11.1 Existing Test Suite Overview
- `backend/src/test/java/com/grocery/ProductControllerTest.java` contains **21 tests**, covering:
  - Unauthenticated access rejection (401).
  - Cross-tenant product isolation (Store A vs Store B).
  - Cross-tenant mutation and deletion blocking.
  - SKU uniqueness within store.
  - Price non-negativity validation.
  - Server-authoritative ID generation (`product.setId(null)`).
  - Safe archival vs. physical deletion lifecycle.
  - Status endpoint RBAC and validation (`PATCH /{id}/status`).

### 11.2 Critical Test Coverage Gaps
1. **Zero Tests for Opening Stock Ledger Recording:** No tests verify that creating a product with initial stock creates an `InventoryTransaction`.
2. **Zero Tests for Barcode Uniqueness:** No tests verify duplicate barcode rejection within the same store.
3. **Zero Tests for Barcode Normalization:** No tests verify that blank/whitespace barcodes are converted to null.
4. **Zero Tests for Concurrent Stock Updates:** No tests evaluate multi-threaded sale checkouts against the same product.
5. **Zero Tests for Product Update Field Preservation:** No tests verify that omitting fields in `PUT /api/v1/products/{id}` does not zero out `minStockLevel` or `taxRate`.
6. **Zero Tests for Update Status Validation:** No tests verify that `PUT` rejects invalid status strings.

---

## 12. FINDINGS AND SEVERITY CLASSIFICATION

### Summary Matrix

| Finding ID | Severity | Category | Title | Affected Files | Status |
|---|---|---|---|---|---|
| **AUD-04-STOCK-01** | **HIGH** | Stock Integrity | Initial product stock bypasses `InventoryTransaction` ledger | `ProductController.java` | Confirmed Defect |
| **AUD-04-STOCK-02** | **HIGH** | Concurrency / Race | Non-atomic Read-Modify-Write on `Product.currentStock` | `SaleController.java`, `InventoryController.java`, `PurchaseController.java` | Confirmed Defect |
| **AUD-04-STOCK-03** | **MEDIUM** | Ledger Deficit | `Math.max(0, ...)` silently masks inventory deficits on overselling | `SaleController.java`, `InventoryController.java` | Confirmed Defect |
| **AUD-04-BARCODE-01** | **HIGH** | Barcode Integrity | Barcode uniqueness is not enforced within store | `ProductController.java`, `Product.java`, `ProductRepository.java` | Confirmed Defect |
| **AUD-04-BARCODE-02** | **MEDIUM** | Barcode Indexing | Missing partial unique index on `(storeId, barcode)` | `Product.java`, `DataSeeder.java` | Confirmed Defect |
| **AUD-04-BARCODE-03** | **MEDIUM** | Barcode Lookups | Ambiguous first-match barcode lookup in POS and search | `pos.html`, `ProductRepository.java` | Confirmed Defect |
| **AUD-04-UPDATE-01** | **HIGH** | Data Loss | Full entity binding on `PUT` overwrites omitted fields with defaults | `ProductController.java` | Confirmed Defect |
| **AUD-04-UPDATE-02** | **MEDIUM** | Security / Integrity | Status validation bypassed on `PUT /api/v1/products/{id}` | `ProductController.java` | Confirmed Defect |
| **AUD-04-UPDATE-03** | **LOW** | UI Consistency | `products.html` displays ineffective stock input on edit | `frontend/products.html` | Confirmed Defect |

---

## 13. DETAILED FINDINGS WITH SOURCE EVIDENCE

### AUD-04-STOCK-01: Initial product stock bypasses `InventoryTransaction` ledger
- **Severity:** HIGH
- **Source:** `ProductController.java` lines 142–165.
- **Evidence:**
  ```java
  Product saved = productRepository.save(product);
  return ResponseEntity.ok(ApiResponse.success("Product created successfully", saved));
  ```
  `inventoryTransactionRepository` is injected into `ProductController`, but it is only used in `deleteProduct` to check historical transactions. When a product is created with `currentStock = 50.0`, no `InventoryTransaction` is written.
- **Root Cause:** Incomplete creation lifecycle omitting `OPENING_STOCK` ledger entry.
- **Impact:** Audit gap; ledger cannot reconstruct total inventory.
- **Remediation:** If `product.getCurrentStock() > 0`, emit an `InventoryTransaction(type: "OPENING_STOCK", quantity: product.getCurrentStock(), balanceAfter: product.getCurrentStock(), referenceType: "INITIAL_STOCK", ...)` inside `createProduct`.

---

### AUD-04-STOCK-02: Non-atomic Read-Modify-Write on `Product.currentStock`
- **Severity:** HIGH
- **Source:** `SaleController.java` lines 132–135, `InventoryController.java` lines 98–102, `PurchaseController.java` lines 129–132.
- **Evidence:**
  ```java
  double newStock = Math.max(0, product.getCurrentStock() - item.getQuantity());
  product.setCurrentStock(newStock);
  productRepository.save(product);
  ```
- **Root Cause:** Standard Spring Data repository `save()` replaces the entire document based on memory state fetched milliseconds earlier.
- **Impact:** Lost updates and phantom stock under concurrent sales or adjustments.
- **Remediation:** Use `MongoTemplate.findAndModify` with atomic `$inc` decrement and criteria `currentStock >= quantity` for stock deductions, or optimistic concurrency control via `@Version`.

---

### AUD-04-BARCODE-01: Barcode uniqueness is not enforced within store
- **Severity:** HIGH
- **Source:** `ProductController.java` lines 135–139, 151–153; `Product.java` line 13.
- **Evidence:**
  `ProductController.createProduct` and `updateProduct` validate SKU:
  ```java
  if (productRepository.existsByStoreIdAndSku(storeId, cleanSku)) {
      return ResponseEntity.badRequest().body(ApiResponse.error("SKU already exists in this store"));
  }
  ```
  No equivalent check exists for `barcode`.
- **Root Cause:** Barcode uniqueness check omitted in application validation and database schema.
- **Impact:** Cashier scanning collisions; wrong items billed.
- **Remediation:** Validate barcode uniqueness per store when barcode is non-null and non-blank.

---

### AUD-04-BARCODE-02: Missing partial unique index on `(storeId, barcode)`
- **Severity:** MEDIUM
- **Source:** `Product.java` line 26; `DataSeeder.java` lines 208–213.
- **Evidence:** Only `@Indexed private String barcode;` is present.
- **Root Cause:** Absence of compound store-scoped unique index with partial filter.
- **Impact:** DB cannot prevent duplicate barcodes across concurrent requests.
- **Remediation:** Add partial compound unique index in `DataSeeder` and `Product.java` with filter `{'barcode': {'$exists': true, '$gt': ''}}`.

---

### AUD-04-UPDATE-01: Full entity binding on `PUT` overwrites omitted fields with defaults
- **Severity:** HIGH
- **Source:** `ProductController.java` lines 172, 212–218.
- **Evidence:**
  ```java
  existing.setMinStockLevel(update.getMinStockLevel());
  existing.setTaxRate(update.getTaxRate());
  ```
  If payload omits `taxRate`, Jackson unmarshalls to `0.0`, wiping out existing tax configurations.
- **Root Cause:** Direct entity binding without DTO or null-safe partial update handling.
- **Impact:** Silent data loss of critical pricing, tax, and alert thresholds.
- **Remediation:** Introduce a dedicated `ProductUpdateRequest` DTO with boxed wrapper types (`Double minStockLevel`, `Double taxRate`) or perform null-safe patching.

---

### AUD-04-UPDATE-02: Status validation bypassed on `PUT /api/v1/products/{id}`
- **Severity:** MEDIUM
- **Source:** `ProductController.java` line 218: `existing.setStatus(update.getStatus());`.
- **Evidence:** While `PATCH /{id}/status` enforces `ALLOWED_STATUSES = Set.of("ACTIVE", "INACTIVE", "ARCHIVED")`, `PUT` accepts any arbitrary string or allows silent un-archival.
- **Root Cause:** Inconsistent validation rules between `PUT` and `PATCH`.
- **Impact:** Invalid status states in MongoDB, breaking POS and dashboard queries.
- **Remediation:** Validate status against `ALLOWED_STATUSES` in `updateProduct`, or reject status changes via `PUT` and require `PATCH /{id}/status`.

---

### AUD-04-UPDATE-03: `products.html` displays ineffective stock input on edit
- **Severity:** LOW
- **Source:** `frontend/products.html` lines 417, 441.
- **Evidence:** Edit modal populates `prod-stock` and submits `currentStock`, but `updateProduct` does not persist it.
- **Root Cause:** Frontend form reuse between creation and editing without disabling immutable/ledger fields.
- **Impact:** User confusion; admin believes stock was modified when it was not.
- **Remediation:** Disable or mark stock field as read-only with a tooltip "To adjust stock, use Inventory Adjustments" when in Edit mode.

---

## 14. PRIORITIZED REMEDIATION PLAN

To ensure safe, testable, and non-breaking delivery, the remediation must be executed in three isolated, sequential stages:

```
[Stage 3: Read-Only Audit] (CURRENT)
         │
         ▼
[Stage 3A: Stock Mutation & Ledger Integrity] (AUD-04-STOCK-01, STOCK-02, STOCK-03)
         │
         ▼
[Stage 3B: Barcode Uniqueness & Lookups] (AUD-04-BARCODE-01, BARCODE-02, BARCODE-03)
         │
         ▼
[Stage 3C: Product Update Safety & DTO] (AUD-04-UPDATE-01, UPDATE-02, UPDATE-03)
```

---

### STAGE 3A: STOCK MUTATION & LEDGER INTEGRITY
- **Target Findings:** `AUD-04-STOCK-01`, `AUD-04-STOCK-02`, `AUD-04-STOCK-03`.
- **Scope Boundaries:**
  - `ProductController.java` (`createProduct` initial stock ledger recording).
  - `SaleController.java`, `InventoryController.java`, `PurchaseController.java` (atomic stock adjustments via `MongoTemplate` or guarded updates).
- **Files to Modify:** `ProductController.java`, `SaleController.java`, `InventoryController.java`.
- **Testing Requirements:**
  - Verify `OPENING_STOCK` transaction is created on `POST /api/v1/products` when `currentStock > 0`.
  - Verify zero stock creation does not generate spurious transactions.
  - Test multi-threaded checkout race condition on single product stock.

---

### STAGE 3B: BARCODE INTEGRITY & LOOKUPS
- **Target Findings:** `AUD-04-BARCODE-01`, `AUD-04-BARCODE-02`, `AUD-04-BARCODE-03`.
- **Scope Boundaries:**
  - Barcode trimming, blank normalization (`"" -> null`), and case handling.
  - Store-scoped barcode uniqueness check in `ProductController` and `ProductRepository`.
  - Migration in `DataSeeder.java` for partial compound unique index `{'storeId': 1, 'barcode': 1}`.
  - POS barcode scanner error handling for duplicate barcode detection.
- **Files to Modify:** `Product.java`, `ProductController.java`, `ProductRepository.java`, `DataSeeder.java`, `frontend/pos.html`.
- **Testing Requirements:**
  - Reject duplicate barcode in same store (HTTP 400).
  - Allow duplicate barcode across different stores (multi-tenant safety).
  - Allow multiple products with null/empty barcodes in the same store.

---

### STAGE 3C: PRODUCT UPDATE SAFETY & DTO DESIGN
- **Target Findings:** `AUD-04-UPDATE-01`, `AUD-04-UPDATE-02`, `AUD-04-UPDATE-03`.
- **Scope Boundaries:**
  - Create `ProductUpdateRequest` DTO to prevent primitive field overwrite data loss.
  - Strictly enforce `ALLOWED_STATUSES` in `PUT` or lock status updates exclusively to `PATCH`.
  - Update `frontend/products.html` to disable stock editing on existing products and link to adjustments.
- **Files to Modify:** `ProductController.java`, `ProductUpdateRequest.java` (new DTO), `frontend/products.html`.
- **Testing Requirements:**
  - Verify partial update preserving existing `minStockLevel` and `taxRate`.
  - Verify rejection of invalid statuses on update.
  - Verify stock field is protected from silent overwrite.

---

## 15. DATABASE MIGRATION CONSIDERATIONS

1. **Partial Compound Unique Index Migration:**
   - Existing index: `store_sku_unique_idx (storeId, sku)`.
   - New index needed: `store_barcode_unique_idx (storeId, barcode)` with partial filter `{barcode: {$type: "string", $gt: ""}}`.
   - **Pre-migration Check:** A query must verify whether `grocery_db` contains pre-existing duplicate barcodes within the same store. If duplicates exist, the index creation must not crash application startup (handled gracefully in `DataSeeder.ensureIndexes()`).
2. **Backfill Empty Barcodes:** Convert all `barcode: ""` to `barcode: null` so index memory remains minimal.

---

## 16. AUDIT LIMITATIONS

- **Live Multi-User Concurrency:** Concurrency conclusions are derived from static source inspection of repository read-modify-write patterns (`product.setCurrentStock(newStock); save(product)`). Verification under high-throughput JMeter/Gatling load tests should be conducted in Stage 3A.
- **Read-Only Scope:** In adherence to strict audit rules, zero database records or source codes were altered.

---

## 17. FINAL AUDIT VERDICT

**AUDIT COMPLETE — REMEDIATION PLAN APPROVED FOR REVIEW**

The Products module stock mutation, barcode, and update mechanisms have been rigorously analyzed. All defects, root causes, risks, and isolation boundaries are thoroughly documented. The 3-phase remediation plan (Stage 3A, 3B, 3C) is formulated and ready for review before execution.
