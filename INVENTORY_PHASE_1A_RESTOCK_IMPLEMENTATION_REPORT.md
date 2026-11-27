# INVENTORY MODULE PHASE 1A — BACKEND RESTOCK / STOCK-IN IMPLEMENTATION REPORT

**Project:** Grocery Management System  
**Phase:** Inventory Module Phase 1A (Backend Only)  
**Date:** 2026-11-27  

---

## 1. Initial Source Inspection Findings

Before modifying any files, the backend models, repositories, security configuration, and test setup were inspected:

1. **Product & Stock Model (`Product.java`)**:
   - `currentStock` is a `double`, `status` is `"ACTIVE"`, `"INACTIVE"`, or `"ARCHIVED"`, and `storeId` is `@Indexed` with a unique compound index `{'storeId': 1, 'sku': 1}`.
   - `ProductController.updateProduct` (`PUT /api/v1/products/{id}`) intentionally preserves `currentStock` and never overwrites it.
2. **Inventory Batch Model (`InventoryBatch.java`)**:
   - Actual fields are `initialQty`, `currentQty`, `purchaseCost`, `inwardDate` (`LocalDate`), `expiryDate` (`LocalDate`), `supplierId`, `supplierName`, `batchNumber` (`@Indexed`), `status` (`"ACTIVE"`), `storeId`, and `createdAt`.
   - Note: `frontend/inventory.html` (out of scope for Phase 1A) currently references mismatched field names (`remainingQuantity`, `initialQuantity`, `costPricePerUnit`) and `InventoryTransaction` fields (`transactionType`, `referenceNumber`, `remarks`). These were documented and left untouched per strict Phase 1A scope.
   - `InventoryBatchRepository` and `DashboardService` query `expiryDate` with `Between` for active batches; `expiryDate = null` is safe and does not match date-range expiry queries.
3. **Inventory Transaction Model (`InventoryTransaction.java`)**:
   - Actual fields are `type`, `quantity`, `balanceAfter`, `referenceType`, `referenceId`, `performedBy`, `notes`, `storeId`, and `createdAt`.
4. **Tenant & Auth Resolution (`TenantContext.java`, `SecurityConfig.java`)**:
   - `TenantContext.requireStoreId(userDetails)` and `TenantContext.requireUsername(userDetails)` derive `storeId` and `username` strictly from `UserPrincipal`.
   - `InventoryController` is annotated with `@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")`.
5. **MongoDB Deployment & Transaction Capability**:
   - `application.properties` connects to standalone MongoDB (`mongodb://127.0.0.1:27017/grocery_db`; test suite uses `grocery_db_test`).
   - No `MongoTransactionManager` or `@Transactional` is configured; standalone MongoDB does not support multi-document transactions. Therefore, a **compensating strategy** (matching `SaleController`) is required.
6. **Idempotency Pattern (`Sale.java`, `ReturnRecord.java`)**:
   - Existing modules use a partial unique compound index `{'storeId': 1, 'idempotencyKey': 1}` (`partialFilter = "{'idempotencyKey': {'$type': 'string'}}"`) plus payload fingerprinting (`SHA-256`) to prevent duplicate execution and reject key reuse with different payloads (`409 Conflict`).

---

## 2. Files Modified and Created

### Created
- `backend/src/main/java/com/grocery/dto/StockInRequest.java`
- `backend/src/test/java/com/grocery/InventoryRestockTest.java`
- `INVENTORY_PHASE_1A_RESTOCK_IMPLEMENTATION_REPORT.md`

### Modified
- `backend/src/main/java/com/grocery/controller/InventoryController.java` (added `POST /api/v1/inventory/restock`, `SupplierRepository` & `MongoTemplate` injection, atomic stock update, batch & ledger creation, idempotency, and compensation helpers)
- `backend/src/main/java/com/grocery/model/InventoryTransaction.java` (added `idempotencyKey`, `idempotencyFingerprint`, and partial unique index `storeId_idempotencyKey_partial_idx`)
- `backend/src/main/java/com/grocery/repository/InventoryBatchRepository.java` (added `existsByStoreIdAndBatchNumber` and `findByStoreIdAndBatchNumber`)
- `backend/src/main/java/com/grocery/repository/InventoryTransactionRepository.java` (added `findByStoreIdAndIdempotencyKey`)

---

## 3. API Contract & Example Request

**Endpoint:** `POST /api/v1/inventory/restock`  
**Authorization:** `Bearer <JWT>` with role `ADMIN`, `STORE_MANAGER`, or `INVENTORY_STAFF`

```json
{
  "productId": "6745abc123def45678901234",
  "quantity": 25.0,
  "purchaseCost": 42.50,
  "supplierId": "6745sup123def45678905678",
  "batchNumber": "BATCH-20261127-CUSTOM01",
  "expiryDate": "2027-03-15",
  "notes": "Morning supplier delivery",
  "idempotencyKey": "restock-req-001"
}
```

- **Required:** `productId` (non-blank), `quantity` (finite, `> 0`)
- **Optional:** `purchaseCost` (defaults to `product.getCostPrice()`), `supplierId`, `batchNumber` (auto-generated if blank), `expiryDate` (`YYYY-MM-DD`, remains `null` if omitted), `notes`, `idempotencyKey`

---

## 4. Example Response

**HTTP `200 OK`**
```json
{
  "success": true,
  "message": "Stock replenished successfully",
  "data": {
    "product": {
      "id": "6745abc123def45678901234",
      "name": "Basmati Rice",
      "currentStock": 35.0,
      "costPrice": 45.5,
      "status": "ACTIVE"
    },
    "batch": {
      "id": "6745bat123def45678909999",
      "storeId": "store-id",
      "productId": "6745abc123def45678901234",
      "productName": "Basmati Rice",
      "batchNumber": "BATCH-20261127-CUSTOM01",
      "supplierId": "6745sup123def45678905678",
      "supplierName": "Fresh Farms Ltd",
      "inwardDate": "2026-11-27",
      "expiryDate": "2027-03-15",
      "initialQty": 25.0,
      "currentQty": 25.0,
      "purchaseCost": 42.5,
      "status": "ACTIVE"
    },
    "transaction": {
      "id": "6745tx123def45678908888",
      "storeId": "store-id",
      "productId": "6745abc123def45678901234",
      "productName": "Basmati Rice",
      "type": "RESTOCK",
      "quantity": 25.0,
      "balanceAfter": 35.0,
      "referenceType": "DIRECT_RESTOCK",
      "referenceId": "BATCH-20261127-CUSTOM01",
      "performedBy": "mgr_a",
      "notes": "Morning supplier delivery"
    }
  },
  "timestamp": "2026-11-27 00:15:45"
}
```

---

## 5. Validation Behavior

All invalid inputs are rejected with `400 Bad Request` (`ApiResponse.error(...)`) before any stock or database mutation:
- Missing or blank `productId`
- Missing, `0`, negative, `NaN`, or `+/-Infinity` `quantity`
- Negative, `NaN`, or `+/-Infinity` `purchaseCost`
- Malformed `expiryDate` (handled by `GlobalExceptionHandler` via `HttpMessageNotReadableException`)
- Non-existent product, cross-store product, or `ARCHIVED` product
- Non-existent or cross-store `supplierId`
- Duplicate custom `batchNumber` within the same store

---

## 6. Security & Tenant Isolation

- Protected by `@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")` on `InventoryController`.
- Unauthenticated requests receive `401 Unauthorized`; unauthorized roles (e.g., `CASHIER`) receive `403 Forbidden`.
- `storeId` and `performedBy` are resolved exclusively from `TenantContext.requireStoreId(userDetails)` and `TenantContext.requireUsername(userDetails)`; client-supplied `storeId` or `performedBy` fields are ignored.
- Product, supplier, batch, and idempotency queries are all scoped by `storeId`.

---

## 7. Atomic Stock Update Implementation

Stock replenishment uses an atomic MongoDB `findAndModify` operation (`returnNew(true)`):
```java
Query stockQuery = new Query(Criteria.where("id").is(productId)
        .and("storeId").is(storeId)
        .and("status").ne("ARCHIVED"));
Update stockUpdate = new Update()
        .inc("currentStock", quantity)
        .set("updatedAt", now);
Product updatedProduct = mongoTemplate.findAndModify(
        stockQuery, stockUpdate, FindAndModifyOptions.options().returnNew(true), Product.class);
```
This eliminates lost-update races under concurrent restock or POS sale operations.

---

## 8. Batch & Ledger Behavior

- **Batch (`InventoryBatch`)**:
  - `initialQty` and `currentQty` are both set to `quantity`.
  - `purchaseCost` defaults to `product.getCostPrice()` when omitted, or uses the validated custom `purchaseCost`.
  - `expiryDate` is persisted only when provided (`null` if omitted; no arbitrary 6-month expiry is fabricated).
  - Auto-generated batch numbers use `BATCH-YYYYMMDD-<8-CHAR-UUID>` and check `existsByStoreIdAndBatchNumber(storeId, candidate)` for collision resistance.
- **Ledger (`InventoryTransaction`)**:
  - Created only after product stock increment and batch persistence succeed.
  - Records `type = "RESTOCK"`, positive `quantity`, `balanceAfter = updatedProduct.getCurrentStock()`, `referenceType = "DIRECT_RESTOCK"`, `referenceId = savedBatch.getBatchNumber()`, and `performedBy = username`.

---

## 9. Idempotency Details

- Optional `idempotencyKey` on `StockInRequest` is trimmed and scoped to `(storeId, idempotencyKey)` via a MongoDB partial unique index (`storeId_idempotencyKey_partial_idx`) on `inventory_transactions`.
- A `SHA-256` `idempotencyFingerprint` is computed from `(productId, quantity, purchaseCost, supplierId, batchNumber, expiryDate, notes)`.
- **Sequential retry** with same key & payload: fast-path lookup returns `200 OK` (`"Stock replenished successfully (idempotent)"`) without mutating stock.
- **Concurrent duplicate** with same key & payload: `DuplicateKeyException` on `InventoryTransaction` triggers compensation (stock rollback + batch deletion) and returns `200 OK` with the winning transaction's data.
- **Key reuse with different payload**: rejected with `409 Conflict`.

---

## 10. Failure Handling & Compensation Strategy

1. **Batch Save Failure**:
   - Atomically decrements `Product.currentStock` by `-quantity` via `rollbackStockIncrement`.
   - Verifies `modifiedCount == 1`. If rollback succeeds, returns `500` stating stock increase was rolled back. If rollback modifies `0` documents or throws, logs `CRITICAL` context and returns `500` stating manual reconciliation is required.
2. **Transaction Ledger Save Failure**:
   - Atomically decrements `Product.currentStock` by `-quantity` AND deletes the newly created `InventoryBatch` (`deleteBatchQuietly`).
   - Verifies both `stockRolledBack` and `batchCleaned`; never claims rollback succeeded if either compensation step fails.

---

## 11. Actual Database Transaction Capability

- The configured MongoDB instance (`mongodb://127.0.0.1:27017`) is standalone with no `MongoTransactionManager` or replica set, so multi-document ACID transactions are **not** available. The endpoint therefore uses atomic single-document `$inc` updates paired with explicit, verified compensation.

---

## 12. Tests Executed & Actual Results

Command executed (using JDK 21 targeting Java 17 bytecode):
```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1'; mvn -f backend\pom.xml test "-Dtest=InventoryRestockTest,ProductControllerTest,MultiTenantIsolationTest,AuthControllerTest,SaleControllerTest"
```

| Test Class | Tests Run | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `InventoryRestockTest` | 10 | 0 | 0 | 0 |
| `ProductControllerTest` | 26 | 0 | 0 | 0 |
| `MultiTenantIsolationTest` | 3 | 0 | 0 | 0 |
| `AuthControllerTest` | 4 | 0 | 0 | 0 |
| `SaleControllerTest` | 16 | 0 | 0 | 0 |
| **Total** | **59** | **0** | **0** | **0** |

`InventoryRestockTest` covers all 41 specified scenarios across functional behavior, validation, security/tenant isolation, batch/ledger failure compensation, compensation failure reporting, concurrent stock-in, and concurrent idempotency.

---

## 13. Database Used for Testing

- `mongodb://127.0.0.1:27017/grocery_db_test` (from `backend/src/test/resources/application.properties`).
- Production/dev database `grocery_db` was never connected to, queried, or modified.

---

## 14. Known Limitations

- `frontend/inventory.html` still has pre-existing field-name mismatches for rendering batches (`remainingQuantity`, `initialQuantity`, `costPricePerUnit`) and transactions (`transactionType`, `referenceNumber`, `remarks`) and does not yet have a Restock UI modal (deferred to frontend phase per strict Phase 1A scope).
- `InventoryBatch.batchNumber` is checked via `existsByStoreIdAndBatchNumber` before insert rather than enforced by a unique MongoDB index (to preserve compatibility with existing seeded/purchase data).

---

## 15. Remaining Risks

- **Standalone MongoDB Compensation Window**: Without a MongoDB replica set, a process crash between the atomic `Product` `$inc` and `InventoryBatch`/`InventoryTransaction` saves could leave an uncompensated stock delta; `CRITICAL` logs are emitted for any detected compensation failure.
- **POS Batch Depletion (FEFO)**: `SaleController` currently deducts `Product.currentStock` without deducting `InventoryBatch.currentQty`; batch-level FEFO deduction remains a separate future phase.
