# MODULE 01 — DASHBOARD
# STAGE 2.2: FINANCIAL INTEGRITY REMEDIATION REPORT

## 1. Original Audit Findings & Final Status

| Audit Finding | Severity | Final Status | Summary |
|---|---|---|---|
| **AUD-01**: Client-supplied `costPrice` trusted at checkout | CRITICAL | **FIXED AND VERIFIED** | `SaleController.processCheckout` now unconditionally overwrites `item.costPrice` from server-side `Product` (`product.getCostPrice() > 0 ? product.getCostPrice() : null`), rejects missing/cross-store/`INACTIVE` products, and assigns `saleItemId`. |
| **AUD-02**: `returns.html` payload mismatch (`totalRefundAmount`/`refundMode` vs `totalRefund`/`refundMethod`) | CRITICAL | **FIXED AND VERIFIED** | `frontend/returns.html` now sends `totalRefund`, `refundMethod`, `idempotencyKey`, and `saleItemId`, displays `totalRefund` + actual `status`, and provides explicit `Confirm Paid` action for `PENDING` returns. |
| **AUD-03**: Unconditional `COMPLETED` status, non-atomic over-refund race, missing idempotency, `null` status treated as `COMPLETED` | CRITICAL / HIGH | **FIXED AND VERIFIED** | Separated `PENDING` acceptance (`POST /returns`) from explicit settlement (`POST /returns/{id}/confirm`, `/fail`, `/reject`), added `storeId_idempotencyKey_partial_idx` + single-doc atomic `MongoTemplate.findAndModify` reservation on `Sale`, and required strict `"COMPLETED".equalsIgnoreCase(status)` in `DashboardService`. |
| **AUD-04**: `DashboardService.computeCogs` ignored returned/restocked quantities | HIGH | **FIXED AND VERIFIED** | `computeCogs(sales, returnsBySale)` now reverses historical COGS strictly for restocked quantities linked by `saleItemId` (or unique `productId`), keeps COGS for non-restocked returns, and marks `cogsAvailable = false` if item-level return details are missing. |
| **AUD-05**: Fully refunded (`REFUNDED`) sales in `ordersCount` / AOV denominator | HIGH | **REQUIRES BUSINESS DECISION** (Documented & Exposed) | Preserved `AOV = Net Sales / Eligible Order Count` (non-cancelled, non-voided) while explicitly exposing `fullyRefundedOrders`, `partiallyRefundedOrders`, and `aovPopulationLabel` on `DashboardMetricsResponse` so the population is transparent without silently altering the formula. |
| **AUD-06**: `LocalDateTime.now()` / `toLocalDate()` sensitive to JVM timezone | MEDIUM | **FIXED AND VERIFIED** | Added `Instant createdAtInstant` on `Sale` (set at checkout alongside IST `LocalDateTime`), added `SaleRepository.findByStoreIdAndIstRange(...)` with legacy `createdAt` fallback, rejected `endDate < startDate` with `400 Bad Request`, and verified under both `UTC` and `Asia/Kolkata` JVM defaults. |

## 2. Files Modified & Created

- **Modified**:
  - `backend/src/main/java/com/grocery/model/Sale.java`
  - `backend/src/main/java/com/grocery/model/ReturnRecord.java`
  - `backend/src/main/java/com/grocery/repository/SaleRepository.java`
  - `backend/src/main/java/com/grocery/repository/ReturnRepository.java`
  - `backend/src/main/java/com/grocery/config/DataSeeder.java`
  - `backend/src/main/java/com/grocery/dto/DashboardMetricsResponse.java`
  - `backend/src/main/java/com/grocery/controller/SaleController.java`
  - `backend/src/main/java/com/grocery/controller/ReportController.java`
  - `backend/src/main/java/com/grocery/service/DashboardService.java`
  - `frontend/returns.html`
  - `e2e/tests/dashboard-e2e.spec.js`
- **Created**:
  - `backend/src/test/java/com/grocery/DashboardStage2_2Test.java`
  - `DASHBOARD_STAGE_2_2_REMEDIATION_REPORT.md`

## 3. Root Cause for Each Corrected Defect

1. **AUD-01**: `SaleController.processCheckout` guarded `item.setCostPrice(product.getCostPrice())` with `if (item.getCostPrice() == null)`, allowing clients to inject arbitrary `costPrice` values and allowing blank `productId` or `INACTIVE` products through checkout.
2. **AUD-02**: `frontend/returns.html` serialized `totalRefundAmount` and `refundMode` while `ReturnRecord` deserialized `totalRefund` and `refundMethod`, causing Jackson to silently bind `0.0` and `"CASH"` on every UI return.
3. **AUD-03**: `SaleController.processReturn` unconditionally set `status = "COMPLETED"`, checked `existingCompletedRefunds` non-atomically before saving, had no `idempotencyKey`, and `DashboardService.loadCompletedReturnsBySale` treated `r.getStatus() == null` as `COMPLETED`.
4. **AUD-04**: `DashboardService.computeCogs` took only `List<Sale>` and never inspected `ReturnRecord.items` or `restocked` flags.
5. **AUD-06**: `SaleController` used `LocalDateTime.now()` (JVM default zone) and `DashboardService` bucketed trends with `sale.getCreatedAt().toLocalDate()` without `Asia/Kolkata` conversion, while silently clamping `endDate < startDate`.

## 4. Refund Lifecycle Design

- **`POST /api/v1/returns` (Acceptance)**: Validates `totalRefund > 0`, `idempotencyKey`, item quantities, and store ownership; atomically reserves `pendingRefundTotal` on `Sale`; persists `ReturnRecord` with `status = "PENDING"`; restocks `restocked = true` items once (`restockProcessed = true`). Does **not** reduce Dashboard Net Sales.
- **`POST /api/v1/returns/{id}/confirm` (Settlement)**: Atomically transitions `PENDING -> COMPLETED` (`findAndModify`), moves `totalRefund` from `sale.pendingRefundTotal` to `sale.refundedTotal`, and sets `Sale.status` to `REFUNDED` or `PARTIALLY_RETURNED`.
- **`POST /api/v1/returns/{id}/fail` & `/reject`**: Atomically transitions `PENDING -> FAILED` or `REJECTED` and releases `sale.pendingRefundTotal`.

## 5. Idempotency & Concurrency Strategy (Standalone MongoDB)

- **Idempotency**: Partial unique compound index `storeId_idempotencyKey_partial_idx` (`{'storeId': 1, 'idempotencyKey': 1}`, `partialFilter: {'idempotencyKey': {'$type': 'string'}}`) plus pre-lookup by `(storeId, idempotencyKey)` and `DuplicateKeyException` fallback. Legacy non-partial `storeId_idempotencyKey_idx` is dropped safely by `DataSeeder.migrateReturnIdempotencyIndex()`.
- **Concurrency**: Single-document atomic `MongoTemplate.findAndModify` on `Sale` using `$expr`:
  `($ifNull($refundedTotal, 0) + $ifNull($pendingRefundTotal, 0) + totalRefund) <= ($grandTotal + 0.001)`.
  Two competing threads cannot both reserve beyond `grandTotal`.

## 6. Historical COGS & Return Calculation

- Formula:
  - `Net COGS = sum((saleItem.quantity - min(saleItem.quantity, restockedQtyForSaleItem)) * saleItem.costPrice)`
  - `Gross Profit = periodNetSales - Net COGS`
  - `Net Profit = Gross Profit - periodExpenses`
- Non-restocked returns (`restocked = false`) do **not** reverse COGS.
- Missing `SaleItem.costPrice`, missing `ReturnRecord.items` on a completed refund, or ambiguous multi-line `productId` without `saleItemId` marks `cogsAvailable = false` and sets `cogs`, `grossProfit`, and `netProfit` to `null`.

## 7. AOV Definition

- `AOV = Net Sales / Eligible Order Count` (`0.0` when `Eligible Order Count == 0`), where `Eligible Order Count` includes all non-`CANCELLED`, non-`VOIDED` sales (`COMPLETED`, `PARTIALLY_RETURNED`, `REFUNDED`).
- `DashboardMetricsResponse` additionally reports `fullyRefundedOrders`, `partiallyRefundedOrders`, and `aovPopulationLabel`.

## 8. Timezone Strategy

- Business day/month boundaries are anchored to `ZoneId.of("Asia/Kolkata")`.
- `SaleController.processCheckout` captures `Instant.now()` in `Sale.createdAtInstant` and IST `LocalDateTime` in `Sale.createdAt`.
- `SaleRepository.findByStoreIdAndIstRange` queries `[startInstant, endInstant)` with fallback to `createdAt` for legacy records lacking `createdAtInstant`.
- Invalid custom ranges (`endDate < startDate`) return `400 Bad Request`.

## 9. Test Names & Assertions (`DashboardStage2_2Test` & `dashboard-e2e.spec.js`)

- `DashboardStage2_2Test.phaseA_serverAuthoritativeCostSnapshot`: Verifies `costPrice` tampering (`1.0`, `0.0`, `99999.0`) is overwritten by `100.0`, rejects cross-store/blank/`INACTIVE` products, and preserves `null` when `Product.costPrice == 0`.
- `DashboardStage2_2Test.phaseBC_refundLifecycleIdempotencyAndConcurrency`: Verifies `0` refund `400`, `PENDING` acceptance without Net Sales reduction, `idempotencyKey` preventing double-restock, `null`/`UNKNOWN_STATE` ignored, `/confirm` reducing Net Sales, and 2-thread concurrent over-refund (`200` + `400`).
- `DashboardStage2_2Test.phaseD_returnAwareHistoricalCogs`: Verifies Scenarios B (restocked partial return), C (non-restocked return), and D (missing item-level return -> `cogsAvailable=false`).
- `DashboardStage2_2Test.phaseEF_aovBreakdownAndTimezoneInvariance`: Verifies `UTC` JVM default with `00:05 IST` vs `23:55 IST` midnight boundary, AOV breakdown fields, and `endDate < startDate` -> `400`.
- `dashboard-e2e.spec.js` (`Stage 2.2 UI-to-API-to-Dashboard`): Drives `returns.html` receipt lookup, item return (`UPI`, `₹100.00`), verifies `PENDING` leaves dashboard at `₹200.00`, clicks `Confirm Paid`, verifies `COMPLETED` and dashboard Net Sales `₹100.00` / Gross `₹200.00`.

## 10. Baseline (TDD Red) vs Post-Fix Output

- **TDD Red Baseline (`DashboardStage2_2Test`)**: `Tests run: 4, Failures: 4, Errors: 0, Skipped: 0` (tampered `costPrice=1.0` persisted instead of `100.0`; `totalRefund=0` accepted with `200`; `/confirm` missing -> `500`; `endDate < startDate` returned `200`).
- **Post-Fix Backend Maven Suite (`mvn -f backend\pom.xml test`)**:
  - `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0` (`BUILD SUCCESS`, `Total time: 26.241 s`).
  - `AuthBrutalTest`: 16/16, `AuthControllerTest`: 4/4, `DashboardStage2Test`: 4/4, `DashboardStage2_2Test`: 4/4, `MultiTenantIsolationTest`: 3/3, `ProductControllerTest`: 6/6.

## 11. Playwright E2E Results (Post-Fix)

- Command: `npx --prefix e2e playwright test --config=e2e/playwright.config.js --workers=1 e2e/tests/dashboard-e2e.spec.js e2e/tests/phase6-full-e2e.spec.js e2e/tests/login-ui.spec.js e2e/tests/auth-e2e.spec.js`
- Output: **`18 passed (59.7s)`** (`auth-e2e.spec.js`: 7/7, `dashboard-e2e.spec.js`: 3/3, `login-ui.spec.js`: 5/5, `phase6-full-e2e.spec.js`: 3/3).

## 12. Database Verification

- All Maven tests ran against `mongodb://127.0.0.1:27017/grocery_db_test` (`backend/src/test/resources/application.properties`).
- Live E2E backend ran with `--spring.data.mongodb.uri=mongodb://127.0.0.1:27017/grocery_db_test`.
- Production `grocery_db` was never accessed or modified.

## 13. Remaining Limitations & Unresolved Issues

- **AUD-05 (Business Decision)**: Fully refunded (`REFUNDED`) orders remain in `todayOrdersCount` / `AOV` denominator (`aovPopulationLabel` documents this). If management prefers excluding fully refunded orders from AOV, filter `status != "REFUNDED"` in `DashboardService.summarizeSales`.
- **External Payment Gateway**: Non-cash (`UPI`/`CARD`) refund confirmation uses staff `/confirm` action; no external gateway webhook is integrated (per scope).
