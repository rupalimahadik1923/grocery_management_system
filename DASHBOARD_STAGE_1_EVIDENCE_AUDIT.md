# Module 01 — Dashboard: Stage 1 Evidence Collection & Business Logic Audit (`DASHBOARD_STAGE_1_EVIDENCE_AUDIT.md`)

## 1. Executive Summary
Completed a full code-level and baseline-test evidence audit of the **Dashboard** module (`frontend/dashboard.html`, `frontend/css/dashboard.css`, `frontend/js/layout.js`, `frontend/js/api.js`, `frontend/js/auth.js`, and `backend/.../ReportController.java`) with **zero** application source code modifications. The baseline backend suite (`29/29` tests on `grocery_db_test`) passes. However, the audit uncovered **6 confirmed defects** (including a `P1` financial/return-accounting gap in `ReportController`, `P2` unrendered `recentSales` table and static `#alerts-list`, `P2` `monthExpenses` summing all-time expenses, `P2` `totalProducts` counting inactive products, and `P2` silent error swallowing) plus **6 open business decisions** requiring stakeholder sign-off before Stage 2 implementation.

---

## 2. Files Inspected
- **Frontend**:
  - `frontend/dashboard.html` (lines 1–142)
  - `frontend/css/dashboard.css` (lines 1–116)
  - `frontend/js/layout.js` (lines 1–127)
  - `frontend/js/api.js` (lines 1–80)
  - `frontend/js/auth.js` (lines 1–61)
- **Backend**:
  - `backend/src/main/java/com/grocery/controller/ReportController.java` (lines 1–149)
  - `backend/src/main/java/com/grocery/security/TenantContext.java`
  - `backend/src/main/java/com/grocery/repository/SaleRepository.java`
  - `backend/src/main/java/com/grocery/repository/ProductRepository.java`
  - `backend/src/main/java/com/grocery/repository/InventoryBatchRepository.java`
  - `backend/src/main/java/com/grocery/repository/ExpenseRepository.java`
  - `backend/src/main/java/com/grocery/model/Sale.java`, `Product.java`, `InventoryBatch.java`, `Expense.java`
- **Tests**:
  - `backend/src/test/java/com/grocery/MultiTenantIsolationTest.java`
  - `backend/src/test/java/com/grocery/AuthBrutalTest.java`

---

## 3. Dashboard Architecture & Frontend-to-Backend Data Flow
1. On page load, `frontend/dashboard.html` calls `Layout.init({ activePage: 'dashboard', title: 'Store Dashboard' })` (`layout.js`), which invokes `Auth.requireAuth()` (`auth.js`) and renders the role-filtered sidebar (`['ADMIN', 'STORE_MANAGER', 'INVENTORY_STAFF']` allowed for `dashboard.html`).
2. `loadDashboardData()` calls `Api.get('/reports/dashboard')` (`api.js`), which attaches `Authorization: Bearer <grocery_token>` and calls `GET /api/v1/reports/dashboard`.
3. `ReportController.getDashboardMetrics(@AuthenticationPrincipal UserDetails userDetails)` is guarded by `@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")`, resolves `storeId` via `tenantContext.requireStoreId(userDetails)`, queries `SaleRepository`, `ProductRepository`, `InventoryBatchRepository`, and `ExpenseRepository` scoped by `storeId`, and returns a `Map<String, Object>`.
4. `dashboard.html` binds 4 KPI DOM elements (`#kpi-today-sales`, `#kpi-total-products`, `#kpi-low-stock`, `#kpi-expiring`) and ignores `todayOrdersCount`, `totalRevenue`, `monthExpenses`, and `recentSales`.

---

## 4. Existing Components, Metrics & Business Logic

| Metric / Component | Frontend Location | Backend Endpoint & Method | Repository Query | Actual Formula / Behavior | Tenant & Role Scope | Empty / Error Behavior |
|---|---|---|---|---|---|---|
| **Today's Sales** | `#kpi-today-sales` (`dashboard.html:36`) | `GET /api/v1/reports/dashboard` (`ReportController.getDashboardMetrics`) | `saleRepository.findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(storeId, startOfToday, endOfToday)` | `sum(Sale.grandTotal)` for server `LocalDate.now()` (`00:00:00` to `23:59:59.999999999`). Includes tax, after discount; does **not** exclude/deduct returns/refunds. | `storeId` from `TenantContext`; `ADMIN`, `STORE_MANAGER`, `INVENTORY_STAFF` | `₹0.00`; silent `console.log` on error |
| **Total Orders (Today)** | **NOT DISPLAYED** in UI (returned in JSON) | `GET /api/v1/reports/dashboard` (`todayOrdersCount`) | Same as Today's Sales | `todaySalesList.size()` | Same | N/A |
| **Total Revenue** | **NOT DISPLAYED** in UI (`reports.html` uses `/reports/sales`) | `GET /api/v1/reports/dashboard` (`totalRevenue`) | `saleRepository.findByStoreIdOrderByCreatedAtDesc(storeId)` | `sum(Sale.grandTotal)` all-time; does **not** deduct returns/refunds. | Same | N/A |
| **Average Order Value** | **NOT IMPLEMENTED** | **NOT IMPLEMENTED** | — | — | — | — |
| **Gross Profit & Net Profit** | **NOT IMPLEMENTED** on Dashboard (`reports.html` uses `/reports/profit-loss`) | `/api/v1/reports/profit-loss` | `SaleRepository` + `ExpenseRepository` | `cogs = revenue * 0.70` (hardcoded 70% estimate); `grossProfit = revenue - cogs`; `netProfit = grossProfit - expenses` | `ADMIN`, `STORE_MANAGER` | N/A |
| **Operating Expenses (`monthExpenses`)** | **NOT DISPLAYED** in UI (returned in JSON) | `GET /api/v1/reports/dashboard` (`monthExpenses`) | `expenseRepository.findByStoreIdOrderByExpenseDateDesc(storeId)` | `sum(Expense.amount)` across **ALL** historical expenses (not filtered to current month). | `storeId` scoped | N/A |
| **Active Products (`totalProducts`)** | `#kpi-total-products` (`dashboard.html:45`) | `GET /api/v1/reports/dashboard` (`totalProducts`) | `productRepository.findByStoreId(storeId)` | `storeProducts.size()` — counts **all** products including `INACTIVE`. | `storeId` scoped | `0` |
| **Low-Stock Products** | `#kpi-low-stock` (`dashboard.html:54`) | `GET /api/v1/reports/dashboard` (`lowStockCount`) | `productRepository.findLowStockProductsByStore(storeId)` | Count where `currentStock <= minStockLevel` (`$expr: $lte`). Includes out-of-stock (`0`). | `storeId` scoped | `0` |
| **Out-of-Stock Products** | **NOT IMPLEMENTED** as separate KPI | **NOT IMPLEMENTED** | — | — | — | — |
| **Expiring Soon (30d)** | `#kpi-expiring` (`dashboard.html:63`) | `GET /api/v1/reports/dashboard` (`expiringCount`) | `batchRepository.findByStoreIdAndExpiryDateBetweenAndStatus(storeId, now, now+30d, "ACTIVE")` | Count of `ACTIVE` batches expiring between `now` and `now + 30 days`. | `storeId` scoped | `0` |
| **Best-Selling Products** | **NOT IMPLEMENTED** | **NOT IMPLEMENTED** | — | — | — | — |
| **Recent Sales Receipts** | `#recent-sales-tbody` (`dashboard.html:90`) | `GET /api/v1/reports/dashboard` (`recentSales`) | `saleRepository.findTop20ByStoreIdOrderByCreatedAtDesc(storeId)` | Backend returns top 20 sales, but **frontend never renders them** into `#recent-sales-tbody`. | `storeId` scoped | Permanently shows `"No sales recorded today yet."` |
| **Inventory Valuation** | **NOT IMPLEMENTED** | **NOT IMPLEMENTED** | — | — | — | — |
| **Sales Trends & Charts** | **NOT IMPLEMENTED** | **NOT IMPLEMENTED** | — | — | — | — |
| **Returns & Refunds** | **NOT IMPLEMENTED** on Dashboard | — | — | Not subtracted from `todaySales` or `totalRevenue`. | — | — |
| **Loyalty Points** | **NOT IMPLEMENTED** on Dashboard | — | — | — | — | — |
| **Notifications & Alerts (`#alerts-list`)** | `#alerts-list` (`dashboard.html:106`) | Not called by `dashboard.html` | — | Hardcoded static HTML (`"System Initialized"`). Topbar bell in `layout.js` is also static/not wired on Dashboard. | — | Static placeholder |

---

## 5. Security & Tenant Isolation Findings
- **Backend RBAC**: `@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")` enforced on `GET /api/v1/reports/dashboard`. `CASHIER` is rejected with `403 Forbidden` (verified in `AuthBrutalTest`). Unauthenticated requests return `401 Unauthorized`.
- **Tenant Isolation**: `storeId` is derived server-side via `TenantContext.requireStoreId(userDetails)`. All 4 underlying repository queries (`SaleRepository`, `ProductRepository`, `InventoryBatchRepository`, `ExpenseRepository`) filter by `storeId`. Client-supplied `storeId` query params are ignored.
- **No Sensitive Leakage**: `getDashboardMetrics()` does not return user password hashes, JWTs, or cross-tenant data.

---

## 6. Requirements Inventory

| Component | Existing Behavior | Expected Behavior | Source Evidence | Status |
|---|---|---|---|---|
| Today's Sales KPI | Displays `todaySales` (`₹0.00` format) | Accurate today's net/gross sales (pending Decision #1) | `dashboard.html:128`, `ReportController.java:43` | **Needs Business Decision** |
| Active Products KPI | Counts all products (`ACTIVE` + `INACTIVE`) | Count only `status = ACTIVE` products | `dashboard.html:44`, `ReportController.java:46` | **Defect (P2)** |
| Low Stock & Expiring KPIs | Counts `<= minStockLevel` and `ACTIVE` batches within 30d | Accurate store-scoped counts | `ReportController.java:47-51` | **Working** |
| Recent Sales Table | Backend returns `recentSales`, UI never renders rows | Render top recent receipts in `#recent-sales-tbody` | `dashboard.html:90-96,126-132` | **Defect (P2)** |
| Attention Needed Alerts | Static hardcoded HTML | Dynamic store-scoped low-stock / expiry / unread alerts | `dashboard.html:106-111` | **Defect (P2)** |
| Month Expenses Metric | Sums all-time expenses under key `monthExpenses` | Filter to current calendar month (or rename) | `ReportController.java:54` | **Defect (P2)** |
| Error State | Silent `console.log` on API failure | Visible error alert / retry state for user | `dashboard.html:133-136` | **Defect (P2)** |
| Mobile Grid Layout | `grid-template-columns: 2fr 1fr` with no `@media` collapse | Stack `.dashboard-grid` to `1fr` on mobile | `dashboard.css:112-116` | **Defect (P3)** |

---

## 7. Test Case Inventory (Baseline & Planned)

| Test ID | Category | Scenario | Expected Result | Actual Result (Baseline) | Status |
|---|---|---|---|---|---|
| `DASH-FUNC-01` | Functional | Dashboard loads for `ADMIN` | 4 KPIs populated from `/reports/dashboard` | Verified in `AuthBrutalTest` / Phase 6 E2E (`200 OK`) | **PASS** |
| `DASH-FUNC-02` | Functional | Recent sales table renders after checkout | `#recent-sales-tbody` shows new receipt | `#recent-sales-tbody` stays `"No sales recorded today yet."` | **FAIL (Defect)** |
| `DASH-FUNC-03` | Functional | Attention Needed shows live alerts | Low-stock/expiring alerts rendered | Hardcoded `"System Initialized"` HTML | **FAIL (Defect)** |
| `DASH-FUNC-04` | Functional | API failure handling | Visible error banner shown | Silent `console.log`, KPIs stay `0` | **FAIL (Defect)** |
| `DASH-CALC-01` | Calculation | `todaySales` after a return/refund | Pending Decision #1 (gross vs net of refunds) | Sums `grandTotal` without deducting `totalRefund` | **BLOCKED (Open Decision)** |
| `DASH-CALC-02` | Calculation | `monthExpenses` with last-month expense | Only current month expenses summed | Sums all-time expenses | **FAIL (Defect)** |
| `DASH-CALC-03` | Calculation | `totalProducts` with `INACTIVE` product | Only `ACTIVE` products counted | Counts `INACTIVE` products too | **FAIL (Defect)** |
| `DASH-SEC-01` | Security | `ADMIN`, `STORE_MANAGER`, `INVENTORY_STAFF` access | `200 OK` | `200 OK` | **PASS** |
| `DASH-SEC-02` | Security | `CASHIER` direct API access | `403 Forbidden` | `403 Forbidden` (Phase 4/6 verified) | **PASS** |
| `DASH-SEC-03` | Security | Unauthenticated access | `401 Unauthorized` | `401 Unauthorized` (`MultiTenantIsolationTest`) | **PASS** |
| `DASH-SEC-04` | Security | Cross-store isolation (Store A vs B) | Store B sees `0` products, `0.0` revenue/expenses | `0` products, `0.0` revenue/expenses (`MultiTenantIsolationTest`) | **PASS** |
| `DASH-BND-01` | Boundary | Zero sales / zero products / zero batches | Returns `0` / `0.0` / `[]` without NPE | Returns `0` / `0.0` / `[]` | **PASS** |

---

## 8. Baseline Execution Results
- **Maven (`mvn -f backend\pom.xml test` on `grocery_db_test`)**: **29/29 PASS** (`AuthBrutalTest`: 16, `AuthControllerTest`: 4, `MultiTenantIsolationTest`: 3, `ProductControllerTest`: 6; 0 failures, 0 errors, 0 skipped).

---

## 9. Confirmed Defects
1. **P1 — Returns/Refunds Not Accounted in Sales/Revenue (`ReportController.java:43,53`)**: `todaySales` and `totalRevenue` sum `Sale.grandTotal` without deducting `ReturnRecord.totalRefund` or excluding `REFUNDED` sales.
2. **P2 — `recentSales` Never Rendered in UI (`dashboard.html:90-96,126-132`)**: Backend returns top 20 sales, but `dashboard.html` never populates `#recent-sales-tbody`.
3. **P2 — Static `#alerts-list` (`dashboard.html:106-111`)**: "Attention Needed" card never loads live notifications/alerts.
4. **P2 — `monthExpenses` Sums All-Time Expenses (`ReportController.java:54`)**: Queries all expenses instead of current month.
5. **P2 — `totalProducts` Includes `INACTIVE` Products (`ReportController.java:46`)**: Label says `Active Products`, but query counts all statuses.
6. **P2 — Silent Error Swallowing (`dashboard.html:133-136`)**: API failures are logged to console only with no UI feedback.
7. **P3 — Missing Mobile Breakpoint for `.dashboard-grid` (`dashboard.css:112-116`)**: Grid stays `2fr 1fr` on narrow screens.

---

## 10. Open Business Decisions (Require Approval Before Stage 2)
1. **Revenue Definition**: Should `todaySales` and `totalRevenue` be **gross of tax** (`grandTotal`) or **net of CGST/SGST**, and should `ReturnRecord.totalRefund` be **deducted**?
2. **COGS & Profit Calculation**: Should `/reports/profit-loss` keep the `0.70 * revenue` estimate or compute actual COGS from `Product.costPrice * quantity`?
3. **Timezone**: Should "Today" use server system timezone or `Asia/Kolkata` (`storeSettings` / `application.properties`)?
4. **`monthExpenses`**: Should `monthExpenses` strictly filter `expenseDate` to the current calendar month?
5. **Scope of New Dashboard Widgets**: Which `NOT IMPLEMENTED` items (e.g., `todayOrdersCount` card, AOV, Top Selling Products, Sales Trend Chart, Out-of-Stock count) should be added in Stage 2?

---

## 11. Recommended Next Steps
1. Review and approve the **Open Business Decisions** above.
2. Proceed to **Module 01 — Stage 2** to fix the confirmed `P1`/`P2`/`P3` defects and implement approved Dashboard metrics/widgets, followed by full backend + Playwright regression verification.

**FINAL STATUS**: **EVIDENCE COMPLETE**
