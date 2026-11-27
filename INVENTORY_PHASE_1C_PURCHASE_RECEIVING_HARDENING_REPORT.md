# INVENTORY MODULE — PHASE 1C: PURCHASE RECEIVING & STOCK INTEGRITY HARDENING REPORT

**Project:** Grocery Management System  
**Endpoints:** `POST /api/v1/purchases`, `POST /api/v1/purchases/{id}/submit`, `POST /api/v1/purchases/{id}/receive`  
**Date:** 2026-11-27  

---

## 1. Original Purchase Receiving Behavior

Before Phase 1C:
- `POST /api/v1/purchases` created a `PurchaseOrder` in `DRAFT` status without validating non-empty items, `orderQty > 0`, `unitCost >= 0`, or non-archived product status, and generated `orderNumber` from `countByStoreId + 101`.
- `POST /api/v1/purchases/{id}/submit` set `status = "ORDERED"` without checking whether the PO was currently `DRAFT`.
- `POST /api/v1/purchases/{id}/receive` only checked `if ("RECEIVED".equals(po.getStatus()))`, immediately saved `po.setStatus("RECEIVED")`, then looped through items using non-atomic `product.getCurrentStock() + item.getOrderQty()` + `productRepository.save(product)`, generated collision-prone 4-digit random batch numbers (`Math.random() * 9000 + 1000`), hardcoded `expiryDate = LocalDate.now().plusMonths(6)`, and had no idempotency or failure compensation.

---

## 2. Problems Discovered

1. **TOCTOU Race on Concurrent Receiving**: Two concurrent `/receive` requests could both read `status = "ORDERED"` and both increment stock, create batches, and create ledger entries.
2. **Premature `RECEIVED` Status**: `po.setStatus("RECEIVED")` was persisted *before* stock/batch/ledger writes, leaving the PO permanently `RECEIVED` even if stock/batch/ledger writes failed.
3. **Weak Status Transition Guards**: `DRAFT` and `CANCELLED` POs could be received directly.
4. **Non-Atomic Read-Modify-Write Stock Updates**: `productRepository.save(product)` could lose concurrent updates from POS sales, restocks, or other POs.
5. **Missing Line-Item & Product Eligibility Validation**: Zero/negative/non-finite quantities, negative costs, and `ARCHIVED` products were not rejected.
6. **Collision-Prone Batch Numbers & Fabricated Expiry Dates**: 4-digit random batch numbers could collide with `storeId_batchNumber_unique_idx`, and `LocalDate.now().plusMonths(6)` fabricated expiry dates.
7. **No Idempotency Protection**: Retrying a `/receive` request after a timeout returned `400` or risked duplicate execution under concurrency.
8. **No Compensation on Partial Failure**: Multi-item receiving failures left partial stock increments, orphan batches, and orphan transactions behind.

---

## 3. Files Modified and Created

### Modified
- `backend/src/main/java/com/grocery/model/PurchaseOrder.java` — Added `receiveIdempotencyKey`, `receiveIdempotencyFingerprint`, `receivedAt`, `receivedBy`, and partial unique index `storeId_receiveIdempotencyKey_partial_idx` (`{'storeId': 1, 'receiveIdempotencyKey': 1}`).
- `backend/src/main/java/com/grocery/repository/PurchaseOrderRepository.java` — Added `findByStoreIdAndReceiveIdempotencyKey(String storeId, String receiveIdempotencyKey)`.
- `backend/src/main/java/com/grocery/controller/PurchaseController.java` — Hardened `createPurchaseOrder`, `submitPurchaseOrder`, and `receiveGoods` with validation, atomic PO claim (`ORDERED -> RECEIVING`), atomic `$inc` stock updates, collision-resistant batch numbers, ledger entries, idempotency, and verified compensation.

### Created
- `backend/src/test/java/com/grocery/PurchaseReceivingTest.java` — Integration & fault-injection test suite for purchase receiving.
- `INVENTORY_PHASE_1C_PURCHASE_RECEIVING_HARDENING_REPORT.md` — This report.

---

## 4. Endpoint Contracts Preserved or Changed

- **Preserved**:
  - `POST /api/v1/purchases` → `ApiResponse<PurchaseOrder>` (`DRAFT`)
  - `POST /api/v1/purchases/{id}/submit` → `ApiResponse<PurchaseOrder>` (`ORDERED`, `404` if not in store)
  - `POST /api/v1/purchases/{id}/receive` → `ApiResponse<PurchaseOrder>` (`RECEIVED`, `404` if not in store, optional JSON body)
- **Additive / Hardened**:
  - `POST /api/v1/purchases/{id}/receive` optional body now supports `idempotencyKey` (`String`) and `expiryDate` (`YYYY-MM-DD`).
  - `DRAFT` / `CANCELLED` POs are rejected on `/receive` (`400 Bad Request`).
  - Concurrent in-flight `/receive` or idempotency conflicts return `409 Conflict`.

---

## 5. Validation Improvements

- **PO Creation**: Requires non-empty `items`, finite `orderQty > 0`, finite `unitCost >= 0`, store-owned supplier, and store-owned non-`ARCHIVED` products; resolves `supplierName` and `productName` server-side.
- **PO Submit**: Only `DRAFT` POs can transition to `ORDERED`.
- **PO Receive**:
  - Validates `expiryDate` format (`YYYY-MM-DD`) if provided.
  - Rejects `DRAFT`, `CANCELLED`, `RECEIVING` (`409`), and `RECEIVED` (`400` unless matching `idempotencyKey`).
  - Pre-validates supplier ownership and all line items (`productId`, finite `orderQty > 0`, finite `unitCost >= 0`, product exists in store, product not `ARCHIVED`) before mutating any state.

---

## 6. Stock Mutation Implementation

- Each line item increments `Product.currentStock` via atomic `mongoTemplate.findAndModify`:
  ```java
  Query stockQuery = new Query(Criteria.where("id").is(prodId)
          .and("storeId").is(storeId)
          .and("status").ne("ARCHIVED"));
  Update stockUpdate = new Update()
          .inc("currentStock", qty)
          .set("updatedAt", now);
  ```
- Scoped strictly to `storeId` from `TenantContext`; `updatedProduct == null` aborts the receive and triggers full compensation.

---

## 7. Batch Integrity

- Creates one `InventoryBatch` per received line item with `storeId`, `productId`, `productName`, `supplierId`, `supplierName`, `inwardDate = LocalDate.now()`, `expiryDate = requestedExpiryDate` (`null` if omitted—no fabricated 6-month expiry), `initialQty = qty`, `currentQty = qty`, `purchaseCost = item.getUnitCost()`, and `status = "ACTIVE"`.
- Generates collision-resistant batch numbers (`BATCH-YYYYMMDD-<8-CHAR-UUID>`) verified against `batchRepository.existsByStoreIdAndBatchNumber(storeId, candidate)`.

---

## 8. Ledger Integrity

- Creates one `InventoryTransaction` per received line item with:
  - `type = "PURCHASE_RECEIPT"`
  - `quantity = +qty`
  - `balanceAfter = updatedProduct.getCurrentStock()`
  - `referenceType = "PURCHASE_ORDER"`
  - `referenceId = po.getOrderNumber()`
  - `performedBy = username` (from `TenantContext`)
  - `storeId = storeId`
  - `notes = "Goods received from PO: " + orderNumber + " (Batch: " + batchNumber + ")"`

---

## 9. Purchase Status and Partial Receiving Behavior

- **Lifecycle**: `DRAFT` → `ORDERED` → `RECEIVING` (atomic claim) → `RECEIVED` (with `receivedAt` and `receivedBy`).
- **Partial Receiving**: Inspected `PurchaseOrder.PurchaseOrderItem`; it has `orderQty`, `unitCost`, and `totalCost`, and **no** `receivedQty` or `remainingQty` fields. Implementing partial receiving would require schema/UI changes outside Phase 1C scope, so full-PO receiving is preserved and partial receiving is documented as unsupported.

---

## 10. Idempotency Implementation

- Store-scoped partial unique index `storeId_receiveIdempotencyKey_partial_idx` on `purchase_orders`.
- SHA-256 fingerprint over `(poId, expiryDate)` (`computeReceiveFingerprint`).
- Sequential retry with matching key & fingerprint returns `200 OK` (`"... (idempotent)"`) without mutating stock.
- Key reuse on a different PO or with different parameters returns `409 Conflict`.
- Concurrent duplicate `/receive` requests are serialized by the atomic `ORDERED -> RECEIVING` claim (`findAndModify`): only one request can claim `RECEIVING`, while concurrent duplicates receive `409 Conflict` (or `200 OK` if the winner already finalized `RECEIVED` with the matching `idempotencyKey`).

---

## 11. Compensation Behavior

Because MongoDB is standalone (no multi-document ACID transactions), `receiveGoods` tracks `incrementedStock`, `createdBatchIds`, and `createdTxIds` across all items. On any failure (stock update, batch save, ledger save, or final PO save):
1. Atomically decrements each incremented product's `currentStock` (`$inc("currentStock", -qty)`) scoped by `id` and `storeId`, checking `modifiedCount == 1`.
2. Removes created batches in `createdBatchIds` scoped by `storeId`, checking `deletedCount == createdBatchIds.size()`.
3. Removes created transactions in `createdTxIds` scoped by `storeId`, checking `deletedCount == createdTxIds.size()`.
4. Reverts `PurchaseOrder.status` from `RECEIVING` back to `ORDERED` (unsetting `receiveIdempotencyKey`, `receiveIdempotencyFingerprint`, `receivedAt`, `receivedBy`), checking `modifiedCount == 1`.
5. If all compensation steps succeed, returns `500` stating all changes were rolled back; if any compensation step fails, logs `CRITICAL` reconciliation context and returns `500` stating `"manual reconciliation required"`.

---

## 12. Tenant Isolation

- `storeId` and `username` come exclusively from `TenantContext`.
- POs, suppliers, products, batches, and transactions are all queried and mutated with `storeId` filters.
- Cross-store `/submit` and `/receive` return `404 Not Found`; cross-store supplier/product references in PO creation/receiving return `400 Bad Request`.
- Role-based access (`ADMIN`, `STORE_MANAGER`) is preserved; `CASHIER`, `INVENTORY_STAFF`, and unauthenticated callers are rejected (`403`/`401`).

---

## 13. Test Results by Test Class

Command executed:
```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1'; mvn -f backend\pom.xml test "-Dtest=PurchaseReceivingTest,InventoryRestockTest,InventoryAdjustmentTest,ProductControllerTest,SaleControllerTest,MultiTenantIsolationTest,AuthControllerTest"
```

| Test Class | Tests Run | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `AuthControllerTest` | 4 | 0 | 0 | 0 |
| `InventoryAdjustmentTest` | 5 | 0 | 0 | 0 |
| `InventoryRestockTest` | 12 | 0 | 0 | 0 |
| `MultiTenantIsolationTest` | 3 | 0 | 0 | 0 |
| `ProductControllerTest` | 26 | 0 | 0 | 0 |
| `PurchaseReceivingTest` | 5 | 0 | 0 | 0 |
| `SaleControllerTest` | 16 | 0 | 0 | 0 |
| **Total** | **71** | **0** | **0** | **0** |

---

## 14. Total Tests, Failures, Errors, and Skipped Tests

- **Total Tests:** 71
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 0

---

## 15. Test Database Confirmation

- Executed strictly against `mongodb://127.0.0.1:27017/grocery_db_test` (`backend/src/test/resources/application.properties`).
- `grocery_db` was never connected to, queried, modified, dropped, or reset.

---

## 16. Remaining Limitations and Risks

- **No Partial Receiving**: `PurchaseOrderItem` does not store `receivedQty` / `remainingQty`; POs are received in full (`ORDERED -> RECEIVED`).
- **Standalone MongoDB**: Multi-document ACID transactions are unavailable; a process crash mid-receiving could leave the PO in `RECEIVING` state requiring manual inspection/reconciliation from `CRITICAL` logs.
- **No Batch-Level FEFO Depletion**: `SaleController` deducts `Product.currentStock` without deducting `InventoryBatch.currentQty`.

---

## 17. Confirmation That Frontend Work Was Not Performed

Confirmed: No files in `frontend/` (`purchases.html`, `inventory.html`, `products.html`, `pos.html`, `dashboard.html`, etc.) were modified in Phase 1C.
