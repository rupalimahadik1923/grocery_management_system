# MODULE 01 — DASHBOARD
# STAGE 2: IMPLEMENTATION AND VERIFICATION REPORT

## 1. Executive Summary

Module 01 (Dashboard) Stage 2 has been implemented and verified end-to-end using a test-driven workflow on the `grocery_db_test` database. All approved financial, inventory, timezone (`Asia/Kolkata`), historical COGS/profit, multi-tenant isolation, XSS safety, error recovery, and responsive layout requirements have been implemented and verified against both backend integration tests (`33/33` passing) and live Playwright browser E2E suites (`17/17` passing across `dashboard-e2e.spec.js`, `phase6-full-e2e.spec.js`, `login-ui.spec.js`, and `auth-e2e.spec.js`).

## 2. TDD Baseline (Red Phase)

Before modifying production dashboard code, `DashboardStage2Test` was created and executed against the Stage 1 codebase, confirming expected baseline failures (`todayGrossSales` missing/null, `totalProducts` counting inactive products, and `monthExpenses` summing all-time expenses instead of current `Asia/Kolkata` calendar month).

## 3. Financial & Revenue Semantics Implemented

- **Gross Sales vs Net Sales**:
  - `todayGrossSales` / `periodGrossSales`: Sum of `grandTotal` (tax-inclusive) across eligible sales (`status` not `CANCELLED` or `VOIDED`).
  - `todayNetSales` / `periodNetSales`: Gross Sales minus deduplicated `COMPLETED` `ReturnRecord.totalRefund` amounts for those sales, capped per sale at `sale.grandTotal`.
  - `PENDING`, `FAILED`, and `REJECTED` refunds are excluded.
  - `todaySales` mirrors `todayNetSales` for backward compatibility.
- **Over-Refund & Refund Status Guard (`SaleController.processReturn`)**:
  - Rejects negative `totalRefund` (`400 Bad Request`).
  - Sums existing `COMPLETED` refunds for `originalSale.getId()` and rejects `400 Bad Request` if `existingCompletedRefunds + totalRefund > originalSale.grandTotal`.
  - Sets `returnRecord.status = "COMPLETED"` and updates `originalSale.status` to `REFUNDED` when fully refunded or `PARTIALLY_RETURNED` otherwise.
- **Order Count & AOV**:
  - `todayOrdersCount` and `periodOrdersCount` count eligible non-cancelled/non-voided sales.
  - `aov = todayOrdersCount > 0 ? round2(todayNetSales / todayOrdersCount) : 0.0`.

## 4. Historical COGS & Profitability

- `Sale.SaleItem` now includes `Double costPrice`.
- `SaleController.processCheckout` snapshots `product.getCostPrice()` onto `Sale.SaleItem.costPrice` when `item.getCostPrice() == null`.
- `DashboardService.computeCogs` computes COGS strictly from `Sale.SaleItem.costPrice * quantity`.
- If **any** eligible sold item in the period has `costPrice == null`, `DashboardService` sets `cogsAvailable = false`, `cogs = null`, `grossProfit = null`, `netProfit = null`, and populates `cogsUnavailableReason` without falling back to current `Product.costPrice` or an estimated margin.

## 5. Timezone (`Asia/Kolkata`) & Period Filtering

- `DashboardService.IST = ZoneId.of("Asia/Kolkata")` is used for `today` (`[todayStart, tomorrowStart)`), `monthExpenses` (`[monthStart, nextMonthStart)`), and configurable date periods (`startDate`, `endDate`, or `days` defaulting to `7`).
- `SaleRepository` and `ExpenseRepository` use explicit MongoDB `@Query` `[gte, lt)` bounds to avoid Spring Data MongoDB criteria collisions on a single field.

## 6. Inventory, Alerts, Top Products & Trend

- **Active Products**: Scoped to `storeId` and `status = "ACTIVE"`.
- **Distinct Stock Buckets**: `outOfStockCount` (`currentStock <= 0`) vs `lowStockCount` (`0 < currentStock <= minStockLevel`).
- **Expiring Soon**: Active `InventoryBatch` records expiring within `[today, today + 7 days]`.
- **Top-Selling Products**: Top 5 by net sold quantity (net of returned item quantities), with deterministic tie-breakers (`netQuantity DESC`, `revenue DESC`, `productName ASC`).
- **Sales Trend**: Daily `Asia/Kolkata` buckets with `date`, `grossSales`, `netSales`, and `ordersCount`.
- **Alerts**: Deduplicated list combining out-of-stock (`CRITICAL`), low-stock (`HIGH`), expiring batches (`HIGH`), and unread store `Notification`s.

## 7. Frontend Dashboard UI & Security Hardening

- Updated `frontend/dashboard.html` and `frontend/css/dashboard.css`:
  - Displays Today's Net & Gross Sales, Today's Orders & AOV, Period Net & Gross Sales, Period Profit (with explicit unavailable notice when `cogsAvailable === false`), Active Products, Low Stock, Out of Stock, Expiring Soon (7d), and Month Expenses.
  - Added Period filter (`Today`, `Last 7 Days`, `Last 30 Days`, `Custom`).
  - Added pure CSS/SVG Sales Trend Chart and Top-Selling Products widget.
  - Populated `#recent-sales-tbody` and `#alerts-list` using `document.createElement` and `textContent` (XSS-safe).
  - Added `#dashboard-status-banner` with loading/error states and `#dashboard-retry-btn`.
  - Added `@media (max-width: 1024px)` and `@media (max-width: 768px)` responsive rules with `min-width: 0` / `overflow-x: hidden` to prevent horizontal overflow on tablet and mobile viewports.

## 8. Files Modified & Created

- `backend/src/main/java/com/grocery/model/Sale.java`
- `backend/src/main/java/com/grocery/model/ReturnRecord.java`
- `backend/src/main/java/com/grocery/repository/SaleRepository.java`
- `backend/src/main/java/com/grocery/repository/ReturnRepository.java`
- `backend/src/main/java/com/grocery/repository/ExpenseRepository.java`
- `backend/src/main/java/com/grocery/repository/ProductRepository.java`
- `backend/src/main/java/com/grocery/controller/SaleController.java`
- `backend/src/main/java/com/grocery/dto/DashboardMetricsResponse.java` (new)
- `backend/src/main/java/com/grocery/service/DashboardService.java` (new)
- `backend/src/main/java/com/grocery/controller/ReportController.java`
- `frontend/dashboard.html`
- `frontend/css/dashboard.css`
- `backend/src/test/java/com/grocery/DashboardStage2Test.java` (new)
- `e2e/tests/dashboard-e2e.spec.js` (new)

## 9. Verification Results (`grocery_db_test`)

- **Backend Maven Test Suite** (`mvn -f backend\pom.xml test`):
  - `Tests run: 33, Failures: 0, Errors: 0, Skipped: 0` (`BUILD SUCCESS`)
  - Breakdown: `AuthBrutalTest` (16/16), `AuthControllerTest` (4/4), `DashboardStage2Test` (4/4), `MultiTenantIsolationTest` (3/3), `ProductControllerTest` (6/6).
- **Playwright Live Browser E2E** (against compiled backend on `:8080` with `grocery_db_test` and frontend on `:3000`):
  - `auth-e2e.spec.js`: `7/7` passed
  - `login-ui.spec.js`: `5/5` passed
  - `phase6-full-e2e.spec.js`: `3/3` passed
  - `dashboard-e2e.spec.js`: `2/2` passed (`DASH-UI-01..09` and `DASH-UI-10..12` verified after final CSS responsive fix)
