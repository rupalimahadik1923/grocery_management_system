# MODULE 01 — DASHBOARD
# STAGE 2.4: FINANCIAL FAILURE-RECOVERY HARDENING REPORT

## 1. Executive Summary

All remaining **MEDIUM** and **LOW** failure-recovery, UI-labelling, and test-coverage findings from Stage 2.3 have been remediated for standalone MongoDB without multi-document transactions, and verified on `grocery_db_test` with **`40/40` Maven backend tests** and **`18/18` Playwright E2E tests** passing.

## 2. Defect-to-Source Mapping & Final Status

| Finding | Source Location | Fix Implemented | Status |
|---|---|---|---|
| **AUD-24-01**: Stranded `pendingRefundTotal` on non-`DuplicateKeyException` save failure or ambiguous save outcome | `Sale.java`, `ReturnRecord.java`, `SaleController.processReturn` | Durable `activeReservations` map (`reservationId -> amount`) on `Sale`; ambiguous save lookup by `(storeId, reservationId)` before releasing; idempotent `releaseReservation` guarded by `activeReservations.{reservationId}` existence; `reconcileStoreReturns` cleanup. | **FIXED AND VERIFIED** |
| **AUD-24-02**: Crash between `ReturnRecord` `COMPLETED` and `Sale` settlement updates | `Sale.java`, `ReturnRecord.java`, `SaleController.confirmReturnRefund`, `DashboardService` | Added `saleSettled` on `ReturnRecord` and `appliedSettlementIds` on `Sale`; atomic `applySettlementToSale` (`appliedSettlementIds != returnId` + `addToSet`); `DashboardService` excludes returns where `reservationId != null && !saleSettled`; `POST /api/v1/returns/reconcile` idempotent recovery. | **FIXED AND VERIFIED** |
| **Stage 2.3 Test Gaps**: Missing Scenario A (full refund + full restock), `/fail`, `/reject`, concurrent `/confirm`, and multi-line `saleItemId` COGS tests | `DashboardStage2_4Test.java` | Added 3 comprehensive test methods verifying all scenarios against `grocery_db_test`. | **FIXED AND VERIFIED** |
| **Stage 2.3 UI Gap**: `aovPopulationLabel` and refunded order counts hidden in dashboard UI | `frontend/dashboard.html` | Added `#kpi-aov-note` under `#kpi-today-aov` displaying `aovPopulationLabel`, `fullyRefundedOrders`, and `partiallyRefundedOrders`. | **FIXED AND VERIFIED** |

## 3. Standalone MongoDB Recovery Architecture

1. **Reservation Failure Recovery (AUD-24-01)**:
   - `SaleController.processReturn` generates a unique `reservationId` and atomically reserves capacity on `Sale` (`inc("pendingRefundTotal", totalRefund)` + `set("activeReservations." + reservationId, totalRefund)`).
   - If `returnRepository.save(returnRecord)` throws any `RuntimeException`, the controller first checks `returnRepository.findByStoreIdAndReservationId(storeId, reservationId)` so an ambiguous write that actually succeeded does not lose its reservation. Otherwise, `releaseReservation` atomically decrements `pendingRefundTotal` and unsets `activeReservations.{reservationId}` (guaranteeing it cannot release twice or more than reserved), and preserves the original exception.
2. **Settlement Failure Recovery (AUD-24-02)**:
   - `confirmReturnRefund` transitions `ReturnRecord` (`PENDING -> COMPLETED`, `saleSettled = false`), then calls `applySettlementToSale(storeId, ret)` which atomically releases `activeReservations.{reservationId}` and applies `inc("refundedTotal", totalRefund)` + `addToSet("appliedSettlementIds", ret.getId())` only when `appliedSettlementIds` does not already contain `ret.getId()`.
   - `Sale.status` is updated atomically via `mongoTemplate.updateFirst` (`Update.set("status", nextStatus)`), followed by `ReturnRecord.saleSettled = true`.
   - `DashboardService.loadCompletedReturnsBySale` ignores any return with `reservationId != null && !saleSettled` until settlement is complete.
   - `POST /api/v1/returns/reconcile` (`ADMIN`, `STORE_MANAGER`) idempotently settles any `COMPLETED` + `saleSettled = false` returns and releases any orphaned `activeReservations` whose `ReturnRecord` does not exist or is no longer `PENDING`.

## 4. Verification Results (`grocery_db_test`)

- **Backend Maven Suite** (`mvn -f backend\pom.xml test`):
  - **`Tests run: 40, Failures: 0, Errors: 0, Skipped: 0`** (`BUILD SUCCESS`, `Total time: 26.808 s`).
  - `AuthBrutalTest` (16/16), `AuthControllerTest` (4/4), `DashboardStage2Test` (4/4), `DashboardStage2_2Test` (4/4), `DashboardStage2_4Test` (3/3), `MultiTenantIsolationTest` (3/3), `ProductControllerTest` (6/6).
- **Playwright Live Browser E2E** (against updated backend on `:8080` with `grocery_db_test` and frontend on `:3000`):
  - **`18 passed (59.7s)`** (`auth-e2e.spec.js` 7/7, `dashboard-e2e.spec.js` 3/3, `login-ui.spec.js` 5/5, `phase6-full-e2e.spec.js` 3/3).
- **Database Isolation**: Production `grocery_db` was never accessed or modified.
