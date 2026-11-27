# INVENTORY MODULE — PHASE 1B ADJUSTMENT HARDENING & STOCK INTEGRITY REPORT

**Project:** Grocery Management System  
**Endpoint:** `POST /api/v1/inventory/adjustments`  
**Date:** 2026-11-27  

---

## 1. Original Adjustment Behavior

Before Phase 1B, `InventoryController.recordAdjustment` (`POST /api/v1/inventory/adjustments`):
- Accepted a `Map<String, Object>` payload with `productId`, `type` (default `"ADJUSTMENT"`), relative `quantity` (positive or negative delta), and `notes` (default `"Physical stock adjustment"`).
- Looked up the product via `productRepository.findByStoreIdAndId(storeId, productId)`.
- Computed `newStock = Math.max(0, oldStock + quantity)` in memory and saved via `productRepository.save(product)`.
- Saved an `InventoryTransaction` (`referenceType = "ADJUSTMENT"`, `referenceId = "ADJ-" + System.currentTimeMillis() % 100000`) and, if `newStock <= minStockLevel`, saved a low-stock `Notification`.

---

## 2. Problems Discovered

1. **Non-Atomic Read-Modify-Write**: Reading `product.getCurrentStock()` and calling `productRepository.save(product)` could lose concurrent updates from simultaneous adjustments or POS sales.
2. **Silent Clamping to Zero**: `Math.max(0, oldStock + quantity)` allowed reducing stock below `0` (e.g., `-50` when `10` available), silently clamping `currentStock` to `0` while recording `-50` in `InventoryTransaction`.
3. **Missing Input Validation**:
   - Missing/null `quantity` or non-numeric `quantity` threw `NullPointerException` / `NumberFormatException` (`500`).
   - `0`, `NaN`, and `Infinity` were accepted.
   - Missing/blank `productId` or non-String `type` could throw `ClassCastException` or query with `null`.
   - Unsupported adjustment `type` values were accepted without validation.
   - `ARCHIVED` products could be adjusted.
4. **No Compensation on Ledger Failure**: If `transactionRepository.save(tx)` failed after `product` was saved, stock remained mutated with no audit ledger entry.
5. **Uncaught Notification Failure**: A failure saving the non-critical low-stock `Notification` could fail the request after stock and ledger were already persisted.

---

## 3. Files Modified & Created

### Modified
- `backend/src/main/java/com/grocery/controller/InventoryController.java` — Hardened `recordAdjustment` (`POST /api/v1/inventory/adjustments`) with validation, atomic `$inc` updates, negative-stock prevention, compensation, and non-critical notification isolation.

### Created
- `backend/src/test/java/com/grocery/InventoryAdjustmentTest.java` — Dedicated functional, validation, security/tenant, compensation, and concurrency test suite.
- `INVENTORY_PHASE_1B_ADJUSTMENT_HARDENING_REPORT.md` — This report.

---

## 4. Endpoint Contract

Preserves the existing contract:
- **Endpoint:** `POST /api/v1/inventory/adjustments`
- **Authorization:** `@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")`
- **Request Body (`Map<String, Object>`)**:
  - `productId`: required, non-blank String
  - `quantity`: required, non-zero finite number (positive for increase, negative for reduction)
  - `type`: optional String, defaults to `"ADJUSTMENT"`. Allowed values: `ADJUSTMENT`, `DAMAGE`, `EXPIRY`, `INTERNAL_USE`
  - `notes`: optional String, defaults to `"Physical stock adjustment"` when omitted or blank
- **Response:** `ApiResponse<InventoryTransaction>` (`200 OK` on success; `400 Bad Request` on validation/insufficient stock; `401`/`403` on auth/role failure; `500` on persistence/compensation failure)

---

## 5. Validation

Rejects with `400 Bad Request` before any DB mutation:
- Null payload
- Missing, non-string, or blank `productId`
- Missing, non-numeric, `0`, `NaN`, or `+/-Infinity` `quantity`
- Non-string or unsupported `type` (outside `ADJUSTMENT`, `DAMAGE`, `EXPIRY`, `INTERNAL_USE`)
- Non-existent or cross-store `productId`
- `ARCHIVED` product
- Reduction where `currentStock < Math.abs(quantity)` (explicitly returns `"Insufficient stock for adjustment..."` without clamping)

---

## 6. Atomic Update Implementation

- **Stock Reduction (`quantity < 0`)**:
  ```java
  Criteria.where("id").is(productId)
          .and("storeId").is(storeId)
          .and("status").ne("ARCHIVED")
          .and("currentStock").gte(Math.abs(quantity))
  ```
  with `new Update().inc("currentStock", quantity).set("updatedAt", now)` via `mongoTemplate.findAndModify(..., returnNew(true), Product.class)`.
- **Stock Increase (`quantity > 0`)**:
  Same atomic `findAndModify` with `Criteria.where("id").is(productId).and("storeId").is(storeId).and("status").ne("ARCHIVED")` and `$inc("currentStock", quantity)`.
- Scoped strictly to `storeId` from `TenantContext.requireStoreId(userDetails)`.

---

## 7. Audit Ledger Implementation

- Every successful adjustment persists an `InventoryTransaction` with:
  - `productId`, `productName`, `storeId`
  - `type`: validated adjustment type (`ADJUSTMENT`, `DAMAGE`, `EXPIRY`, `INTERNAL_USE`)
  - `quantity`: signed delta (`+` or `-`)
  - `balanceAfter`: `updatedProduct.getCurrentStock()` from the atomic `findAndModify` result
  - `referenceType`: `"ADJUSTMENT"`
  - `referenceId`: `"ADJ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()`
  - `performedBy`: `TenantContext.requireUsername(userDetails)`
  - `notes`: validated/defaulted reason
- Rejected adjustments never mutate stock and never create ledger entries.

---

## 8. Compensation Behavior

- Because MongoDB is standalone (no multi-document ACID transactions), if `transactionRepository.save(tx)` throws an exception after the atomic `Product` update:
  - `rollbackStockIncrement(storeId, productId, quantity, now)` executes an atomic `mongoTemplate.updateFirst` (`$inc("currentStock", -quantity)`) scoped by `id` and `storeId`.
  - Checks `result.getModifiedCount() == 0`.
  - If rollback succeeds: returns `500` stating `"Adjustment failed while recording audit ledger; stock change was rolled back"`.
  - If rollback fails: logs `CRITICAL` context and returns `500` stating `"manual reconciliation required"`.
- Low-stock `Notification` creation is wrapped in `try/catch` so notification errors log a warning without failing an already-committed adjustment.

---

## 9. Concurrency Handling

- Atomic `$inc` + conditional `currentStock >= abs(quantity)` guarantees that:
  - Parallel increases accumulate without lost updates.
  - Parallel reductions against limited stock cannot drive `currentStock` below `0`; only requests whose atomic condition matches succeed, and the rest fail cleanly with `400 Bad Request` (`Insufficient stock`).
  - Cross-store adjustments are rejected without affecting the target store's stock.

---

## 10. Tests Executed

Command executed:
```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1'; mvn -f backend\pom.xml test "-Dtest=InventoryAdjustmentTest,InventoryRestockTest,ProductControllerTest,SaleControllerTest,MultiTenantIsolationTest,AuthControllerTest"
```

---

## 11. Test Results

| Test Class | Tests Run | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `AuthControllerTest` | 4 | 0 | 0 | 0 |
| `InventoryAdjustmentTest` | 5 | 0 | 0 | 0 |
| `InventoryRestockTest` | 12 | 0 | 0 | 0 |
| `MultiTenantIsolationTest` | 3 | 0 | 0 | 0 |
| `ProductControllerTest` | 26 | 0 | 0 | 0 |
| `SaleControllerTest` | 16 | 0 | 0 | 0 |
| **Total** | **66** | **0** | **0** | **0** |

---

## 12. Database Used

- `mongodb://127.0.0.1:27017/grocery_db_test` (from `backend/src/test/resources/application.properties`).
- `grocery_db` was never connected to, queried, modified, dropped, or reset.

---

## 13. Remaining Limitations

- **Standalone MongoDB**: Multi-document ACID transactions are not supported; a process crash between the atomic `Product` `$inc` and `InventoryTransaction` save would require log-assisted reconciliation.
- **No Batch-Level Deduction on Adjustment**: Per strict Phase 1B scope, adjustments update `Product.currentStock` and `InventoryTransaction` without modifying `InventoryBatch.currentQty` (batch/FEFO depletion is out of scope).
- **Frontend Untouched**: `frontend/inventory.html` was not modified in Phase 1B.
