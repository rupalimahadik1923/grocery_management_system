# INVENTORY MODULE — PHASE 1A.1 FINAL INTEGRITY VERIFICATION REPORT

**Project:** Grocery Management System  
**Endpoint:** `POST /api/v1/inventory/restock`  
**Date:** 2026-11-27  

---

## 1. Review Findings

A focused read-only inspection of `InventoryController.java`, `StockInRequest.java`, `InventoryBatch.java`, `InventoryTransaction.java`, `DataSeeder.java`, related repositories, and `InventoryRestockTest.java` identified two concrete concurrency/integrity defects in the initial Phase 1A implementation:

1. **TOCTOU Race on Custom `batchNumber` Uniqueness**:
   - `InventoryController.restockInventory` only checked `batchRepository.existsByStoreIdAndBatchNumber(storeId, requestedBatchNumber)` before saving `InventoryBatch`.
   - Without a database-enforced unique index on `(storeId, batchNumber)`, two concurrent requests with the same custom `batchNumber` could both pass the existence check and insert duplicate batches in the same store (which would also cause `batchRepository.findByStoreIdAndBatchNumber` to fail with `IncorrectResultSizeDataAccessException`).
2. **`DuplicateKeyException` Handling on `InventoryBatch` Save**:
   - With a unique index on `(storeId, batchNumber)`, concurrent requests using the same custom `batchNumber`—or concurrent identical idempotent requests that also specify a custom `batchNumber`—can hit `DuplicateKeyException` at `batchRepository.save(batch)` (Step 7) before reaching `transactionRepository.save(tx)` (Step 8).
   - Previously, `batchRepository.save(batch)` only caught generic `Exception` and returned `500 Internal Server Error` instead of resolving idempotency (`200 OK` / `409 Conflict`) or returning `409 Conflict` for duplicate batch collision.

Both defects were fixed with minimal, narrowly scoped edits.

---

## 2. Batch-Number Uniqueness Review

- **Migration-Safe Index Enforcement**:
  - Added `migrateInventoryBatchNumberIndex()` in `DataSeeder.java` (following the existing `migrateCustomerPhoneIndex()` and `migrateSupplierCodeIndex()` pattern).
  - Before creating `storeId_batchNumber_unique_idx` (`{'storeId': 1, 'batchNumber': 1}`, `unique = true`), `DataSeeder` inspects existing `inventory_batches` documents for missing `storeId` or intra-store duplicate `batchNumber` pairs.
  - If legacy duplicates or missing `storeId` values exist, index creation is skipped with a warning log rather than crashing startup or silently deleting/modifying legacy records.
  - On clean data (verified in `grocery_db_test`), `storeId_batchNumber_unique_idx` is created and enforced by MongoDB.
- **Controller Handling**:
  - `InventoryController` keeps the fast pre-check (`400 Bad Request`) AND catches `DuplicateKeyException` on `batchRepository.save(batch)` (`409 Conflict` after rolling back `Product.currentStock`, or idempotent resolution if `idempotencyKey` was supplied).
  - Cross-store reuse of the same `batchNumber` remains allowed because the unique index is compound on `(storeId, batchNumber)`.

---

## 3. Idempotency Concurrency Review

- **Key & Store Scoping**: `InventoryTransaction` enforces uniqueness via `storeId_idempotencyKey_partial_idx` (`{'storeId': 1, 'idempotencyKey': 1}`, `unique = true`, `partialFilter = "{'idempotencyKey': {'$type': 'string'}}"`). Requests without `idempotencyKey` omit the field (`null`) and are never blocked by the partial index.
- **Fingerprinting**: `computeRestockFingerprint` hashes `(productId, quantity, purchaseCost, supplierId, requestedBatchNumber, expiryDate, notes)` with `SHA-256` after consistent normalization (`trim()` on strings, `notes` defaulting to `"Direct stock replenishment"`, `supplierId` resolved/trimmed).
- **Winner / Loser Behavior**:
  - **Sequential duplicate**: Fast-path lookup finds existing `InventoryTransaction`, verifies fingerprint, and returns `200 OK` (or `409 Conflict` on mismatch) without mutating stock.
  - **Concurrent identical requests**:
    - If they race into `transactionRepository.save(tx)`, the loser catches `DuplicateKeyException`, atomically rolls back its own `Product.currentStock` increment (`-quantity`), deletes **only** its own `savedBatch.getId()` (scoped by `storeId`), and returns `200 OK` with the winner's persisted `product`, `batch`, and `transaction`.
    - If they also share a custom `batchNumber` and race at `batchRepository.save(batch)`, the loser catches `DuplicateKeyException`, rolls back its own stock increment, checks `findByStoreIdAndIdempotencyKey(storeId, idemKey)`, and returns `200 OK` (if fingerprint matches) or `409 Conflict`.
  - **Concurrent conflicting requests** (same `idempotencyKey`, different payload): Winner persists `200 OK`; loser rolls back its own stock/batch and returns `409 Conflict`.
  - **Cleanup Order**: Stock rollback and own-batch deletion execute before returning the idempotent or conflict response, and the loser never touches the winner's `batch` or `transaction`.

---

## 4. Compensation Safety Review

- **Product Stock Rollback (`rollbackStockIncrement`)**:
  - Uses atomic `mongoTemplate.updateFirst` scoped by `Criteria.where("id").is(productId).and("storeId").is(storeId)` with `$inc("currentStock", -quantity)`.
  - Checks `result.getModifiedCount() == 0`; logs `CRITICAL` context (`storeId`, `productId`, `quantity`) and returns `false` if no document was modified or an exception occurred.
- **Batch Cleanup (`deleteBatchQuietly`)**:
  - Uses `mongoTemplate.remove` scoped by `Criteria.where("id").is(batchId).and("storeId").is(storeId)`.
  - Checks `result.getDeletedCount() == 0`; cannot delete batches belonging to another store or another request.
- **Failure Surfacing**:
  - If compensation fails, the API returns `500 Internal Server Error` explicitly stating `"manual reconciliation required"` and never claims rollback succeeded.
- **ACID Limitation**: Standalone MongoDB does not provide multi-document ACID transactions; under concurrent POS sales (`SaleController`) and restocks, `Product.currentStock` uses atomic `$inc` so increments/decrements do not overwrite each other, though a process crash between steps would require manual reconciliation from `CRITICAL` logs.

---

## 5. Files Modified

- `backend/src/main/java/com/grocery/config/DataSeeder.java` — Added `migrateInventoryBatchNumberIndex()` to safely enforce `storeId_batchNumber_unique_idx` when no legacy duplicates exist.
- `backend/src/main/java/com/grocery/controller/InventoryController.java` — Added `DuplicateKeyException` handling on `batchRepository.save(batch)` with stock rollback and idempotency/conflict resolution.
- `backend/src/test/java/com/grocery/InventoryRestockTest.java` — Added Phase 1A.1 concurrency, unique-index, and cross-store tests.
- `INVENTORY_PHASE_1A_1_FINAL_INTEGRITY_REPORT.md` — Created this verification report.

---

## 6. Tests Added or Changed

Added 2 new integration test methods to `InventoryRestockTest.java` (bringing it from 10 to 12 `@Test` methods):
1. `uniqueBatchNumberIndexPersisted_andPreventsConcurrentDuplicates`:
   - Verifies `storeId_batchNumber_unique_idx` exists and is `unique` in `grocery_db_test`.
   - Fires 4 concurrent requests with the same custom `batchNumber`; asserts exactly 1 succeeds (`200`), 3 are rejected (`400`/`409`), stock increases once (`12.0`), and only 1 batch is persisted.
2. `concurrentConflictingIdempotencyKeys_andCrossStoreBatchIsolation`:
   - Verifies the same custom `batchNumber` is permitted across Store A and Store B.
   - Fires 2 concurrent requests in Store A with the same `idempotencyKey` and different quantities (`10.0` vs `30.0`); asserts 1 returns `200` and 1 returns `409`, stock reflects only the winner, and the loser leaves no orphan batch.

---

## 7. Tests Executed & Actual Results

Command executed:
```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1'; mvn -f backend\pom.xml test "-Dtest=InventoryRestockTest,ProductControllerTest,MultiTenantIsolationTest,AuthControllerTest,SaleControllerTest"
```

| Test Class | Tests Run | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `AuthControllerTest` | 4 | 0 | 0 | 0 |
| `InventoryRestockTest` | 12 | 0 | 0 | 0 |
| `MultiTenantIsolationTest` | 3 | 0 | 0 | 0 |
| `ProductControllerTest` | 26 | 0 | 0 | 0 |
| `SaleControllerTest` | 16 | 0 | 0 | 0 |
| **Total** | **61** | **0** | **0** | **0** |

---

## 8. Real MongoDB vs Mock Coverage

- **Real MongoDB (`grocery_db_test`)**:
  - All functional restock tests, validation tests, security/tenant isolation tests, unique-index persistence check, concurrent custom batch collision, concurrent stock-in, concurrent idempotent duplicates, and concurrent conflicting idempotency keys execute against real MongoDB (`127.0.0.1:27017/grocery_db_test`) via `MockMvc` + `MongoTemplate`.
- **Mockito `@SpyBean` Fault Injection**:
  - `batchSaveFailure_rollsBackStockAndCreatesNoLedger`, `ledgerSaveFailure_rollsBackStockAndDeletesOrphanBatch`, and `compensationFailure_isReportedAccurately` use `@SpyBean` on `InventoryBatchRepository` and `InventoryTransactionRepository` to simulate write/rollback failures, while still asserting real MongoDB state (`Product`, `InventoryBatch`, `InventoryTransaction`).

---

## 9. Database Used

- `mongodb://127.0.0.1:27017/grocery_db_test` (configured in `backend/src/test/resources/application.properties`).
- `grocery_db` was never connected to, queried, modified, dropped, or reset.

---

## 10. Remaining Risks and Limitations

- **Standalone MongoDB**: Multi-document ACID transactions are not available; a JVM/network crash mid-operation between `Product` `$inc` and `InventoryBatch`/`InventoryTransaction` persistence would require log-assisted reconciliation.
- **Legacy Duplicate Data in Non-Test Databases**: If `grocery_db` contains pre-existing duplicate `(storeId, batchNumber)` records or batches missing `storeId`, `DataSeeder.migrateInventoryBatchNumberIndex()` will intentionally skip creating `storeId_batchNumber_unique_idx` and log a warning; a one-time data cleanup migration would be required before the unique index can activate there.
- **No Browser/UI Verification**: Phase 1A / 1A.1 is strictly backend-only; frontend integration is deferred.

---

## 11. Final Status

**VERIFIED WITH LIMITATIONS**
