# MODULE 01 — DASHBOARD
# STAGE 2.6: RESERVATION RACE & RETURN QUANTITY INTEGRITY REMEDIATION REPORT

## 1. Executive Summary

Stage 2.6 remediated the two remaining **HIGH** defects identified in the Stage 2.5 audit (`AUD-26-01` reservation reconciliation race with in-flight `processReturn`, and `AUD-26-02` missing upper-bound return quantity validation against original `SaleItem` and prior returns) for standalone MongoDB without multi-document transactions. Both fixes and all 14 targeted regression/concurrency tests were verified on `grocery_db_test` (`42/42` Maven tests, `18/18` Playwright E2E tests).

## 2. AUD-26-01 — Root Cause & Remediation Architecture

- **Root Cause**: `SaleController.reconcileStoreReturns` previously checked `returnRepository.findByStoreIdAndReservationId` without reservation state or age coordination. An in-flight `processReturn` that had reserved `Sale.pendingRefundTotal` but not yet persisted `ReturnRecord` could have its reservation prematurely released by a concurrent `reconcileStoreReturns` call.
- **Remediation Architecture (`Sale.ReservationMetadata`)**:
  - `Sale` now stores `reservationMetadata: Map<String, ReservationMetadata>` containing `status` (`RESERVED`, `LINKED`, `RECOVERY_CLAIMED`), `createdAtEpochMs`, and `itemQuantities`.
  - **Normal `processReturn` Flow**:
    1. Atomically reserves capacity and inserts `reservationMetadata.{resId} = { status: "RESERVED", createdAtEpochMs, itemQuantities }`.
    2. Persists `ReturnRecord` (`status = "PENDING"`).
    3. Atomically transitions `reservationMetadata.{resId}.status` from `RESERVED -> LINKED` (`mongoTemplate.findAndModify`) before restocking. If a reconciler already claimed `RECOVERY_CLAIMED`, `processReturn` deletes the un-restocked `ReturnRecord` and returns `409 Conflict` without restocking.
  - **Reconciler (`reconcileStoreReturns`) Protections**:
    1. Step 1 settles all `COMPLETED` + `saleSettled = false` returns first.
    2. Step 2 skips `LINKED` reservations with active `PENDING` (or unsettled `COMPLETED`) returns.
    3. For `RESERVED` reservations, checks both `now - createdAtEpochMs >= reservationGracePeriodMs` (30s default) AND `findByStoreIdAndReservationId == empty`, then atomically claims `RESERVED -> RECOVERY_CLAIMED` via `mongoTemplate.findAndModify`. Only the single winning thread double-checks `findByStoreIdAndReservationId` and calls `releaseReservation`.

## 3. AUD-26-02 — Root Cause & Return Quantity Validation Strategy

- **Root Cause**: `SaleController.processReturn` only checked `item.getQuantity() <= 0` and did not validate returned item quantities against original `Sale.SaleItem.quantity`, previously completed returns, or active pending returns.
- **Validation & Atomic Item-Level Reservation Strategy**:
  1. Every `ReturnItem` is matched to an exact `Sale.SaleItem` on `originalSale`: explicit `saleItemId` must exist on `originalSale` (unknown or cross-sale `saleItemId`s are rejected with `400 Bad Request`); omitted `saleItemId` is permitted only when `productId` uniquely matches 1 `SaleItem` on `originalSale` (multi-line ambiguity is rejected with `400 Bad Request`). Mismatched `productId` is rejected.
  2. `Sale` tracks `itemReturnedQty` and `itemPendingReturnQty` keyed by `saleItemId`.
  3. The same atomic `mongoTemplate.findAndModify` on `Sale` that reserves `pendingRefundTotal` enforces `$expr` `$and` bounds for every `(saleItemId, reqQty)`:
     `($ifNull($itemReturnedQty.{sid}, 0) + $ifNull($itemPendingReturnQty.{sid}, 0) + reqQty) <= soldQty + 0.001`
     and atomically increments `itemPendingReturnQty.{sid}`.
  4. `releaseReservation` decrements `itemPendingReturnQty.{sid}` atomically using `meta.itemQuantities`; `applySettlementToSale` increments `itemReturnedQty.{sid}` once under the `appliedSettlementIds` guard.

## 4. Files Changed & Created

- **Modified**:
  - [Sale.java](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/model/Sale.java) (`ReservationMetadata`, `reservationMetadata`, `itemReturnedQty`, `itemPendingReturnQty`)
  - [SaleController.java](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/controller/SaleController.java) (`processReturn`, `applySettlementToSale`, `reconcileStoreReturns`, `releaseReservation`)
- **Created**:
  - [DashboardStage2_6Test.java](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/test/java/com/grocery/DashboardStage2_6Test.java) (Tests 1–14)
  - `DASHBOARD_STAGE_2_6_REMEDIATION_REPORT.md`

## 5. Verification Results (`grocery_db_test`)

- **Backend Maven Suite** (`mvn -f backend\pom.xml test`):
  - **`Tests run: 42, Failures: 0, Errors: 0, Skipped: 0`** (`BUILD SUCCESS`, `Total time: 38.595 s`).
  - `AuthBrutalTest` (16/16), `AuthControllerTest` (4/4), `DashboardStage2Test` (4/4), `DashboardStage2_2Test` (4/4), `DashboardStage2_4Test` (3/3), `DashboardStage2_6Test` (2/2 covering Tests 1–14), `MultiTenantIsolationTest` (3/3), `ProductControllerTest` (6/6).
- **Playwright Live Browser E2E** (against updated backend on `:8080` with `grocery_db_test` and frontend on `:3000`):
  - **`18 passed (1.3m)`** (`auth-e2e.spec.js` 7/7, `dashboard-e2e.spec.js` 3/3, `login-ui.spec.js` 5/5, `phase6-full-e2e.spec.js` 3/3).
- **Database Isolation**: Production `grocery_db` was never accessed or modified.

## 6. Remaining Limitations & Verification Status

- Partial mid-loop inventory restock crash recovery (`productRepository.save` vs `transactionRepository.save` before `restockProcessed = true`) remains non-transactional on standalone MongoDB.
- Mocked `RuntimeException` injection during `returnRepository.save` (Stage 2.5 Scenarios A–D) was verified via code inspection and orphan-state simulation rather than fault-injection mocks.
- Per Stage 2.6 completion rules, Module 01 is submitted for review without claiming final acceptance or starting Module 02.
