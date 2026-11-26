# MODULE 01 — DASHBOARD
# STAGE 2.3: FINAL ACCEPTANCE AND FAILURE-PATH AUDIT REPORT

## 1. Executive Summary

**Final Acceptance Decision: `CONDITIONALLY ACCEPTED`**

A read-only source, failure-path, and test-evidence audit of Module 01 (Dashboard) Stage 2.2 remediation confirms that the previously rejected CRITICAL financial defects (`AUD-01` client-supplied `costPrice` tampering, `AUD-02` `returns.html` payload mismatch, `AUD-03` unconditional `COMPLETED` status and non-atomic over-refund race, `AUD-04` return-blind COGS, and `AUD-06` JVM-timezone sensitivity) have been remediated in code and verified against `grocery_db_test` (`37/37` Maven tests, `18/18` Playwright E2E tests).

No unresolved **CRITICAL** defects remain in normal financial paths. However, clearly scoped **MEDIUM** and **LOW** failure-path recovery gaps, UI labelling gaps, and specific test-coverage gaps remain (including missing explicit tests for `/fail`, `/reject`, and Scenario A full-refund/full-restock COGS reversal, plus uncompensated `pendingRefundTotal` if `ReturnRecord` persistence fails with a non-`DuplicateKeyException` error).

---

## 2. Refund Lifecycle Review

Source: [SaleController.java:202-432](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/controller/SaleController.java#L202-L432), [ReturnRecord.java:32-44](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/model/ReturnRecord.java#L32-L44), [DashboardService.java:204-212](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/service/DashboardService.java#L204-L212)

- **Acceptance (`POST /api/v1/returns`)**: Validates `totalRefund > 0`, `idempotencyKey`, item quantities, and store ownership; reserves capacity on `Sale`; persists `ReturnRecord` with `status = "PENDING"` (`line 298`).
- **Settlement (`POST /api/v1/returns/{id}/confirm`, `/fail`, `/reject`)**:
  - `/confirm` (`lines 351-393`) atomically transitions `PENDING -> COMPLETED`, moves `totalRefund` from `pendingRefundTotal` to `refundedTotal`, and updates `Sale.status` (`REFUNDED` or `PARTIALLY_RETURNED`).
  - `/fail` (`lines 395-400`) and `/reject` (`lines 402-407`) atomically transition `PENDING -> FAILED` or `REJECTED` via `transitionPendingReturn` (`lines 409-425`) and release `pendingRefundTotal`.
- **Dashboard Aggregation**: `DashboardService.loadCompletedReturnsBySale` (`line 206`) strictly requires `"COMPLETED".equalsIgnoreCase(r.getStatus())`; `PENDING`, `FAILED`, `REJECTED`, `null`, and unknown statuses are excluded.

---

## 3. Inventory Lifecycle Review

Source: [SaleController.java:317-346](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/controller/SaleController.java#L317-L346)

1. **Physical receipt vs settlement**: Physical inventory restocking (`restocked = true`) executes during `POST /api/v1/returns` (return acceptance, while `status = "PENDING"`), **independently** of refund settlement.
2. **Restock before confirmation**: Yes—intended for physical counter returns where goods are received before payment settlement completes.
3. **`FAILED` / `REJECTED` refund effect on restocked inventory**: Already-restocked inventory remains in `Product.currentStock` and is recorded in `InventoryTransaction` (`type = "CUSTOMER_RETURN"`, `referenceNumber = returnNumber`, `lines 328-340`). There is no automated un-restock if a return is later marked `REJECTED`.
4. **Ledger & duplicate protection**: Every restock writes an `InventoryTransaction` entry and sets `restockProcessed = true` (`line 344`). Repeated requests with the same `idempotencyKey` return early at `lines 218-223` (or `lines 308-313`) without re-restocking.
5. **Consistency risk**: Because `ReturnRecord` does not require `items` when `saleId` is provided, a `ReturnRecord` without `items` can still be confirmed as `COMPLETED` (causing `DashboardService.computeCogs` to mark COGS unavailable).

---

## 4. Atomic Reservation & Failure Recovery Review

Source: [SaleController.java:247-393](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/controller/SaleController.java#L247-L393)

- **Individually atomic operations**:
  - `Sale` capacity reservation (`mongoTemplate.findAndModify` with `$expr`, `lines 259-277`) is atomic at the single-document level.
  - `ReturnRecord` status transitions (`/confirm`, `/fail`, `/reject`) filter on `status = "PENDING"` via `mongoTemplate.findAndModify`, ensuring single-document atomicity.
- **Failure-path & crash-recovery gaps (Standalone MongoDB — no multi-document transactions)**:
  1. **[MEDIUM] Stranded `pendingRefundTotal` on non-`DuplicateKeyException` failure**: If `Sale` reservation succeeds (`line 272`) and `returnRepository.save(returnRecord)` (`line 303`) throws any exception other than `DuplicateKeyException`, `releasePendingOnSale` is **not** called, permanently stranding `pendingRefundTotal` on `Sale`.
  2. **[MEDIUM] Partial restock crash**: If `returnRepository.save` succeeds (`line 303`) and the process crashes mid-loop during `productRepository.save` (`line 326`), `ReturnRecord` remains `PENDING` with `restockProcessed = false` and no automated retry/reconciliation job exists.
  3. **[MEDIUM] Crash between `ReturnRecord` `COMPLETED` and `Sale` update**: In `confirmReturnRefund`, if the JVM crashes between `line 361` (`ReturnRecord` set to `COMPLETED`) and `line 381` (`Sale` `pendingRefundTotal`/`refundedTotal` update), `Sale.pendingRefundTotal` remains elevated and `Sale.status` stale; no reconciliation job exists.
  4. **[LOW] Non-atomic `saleRepository.save(updatedSale)`**: `confirmReturnRefund` (`line 388`) calls `saleRepository.save(updatedSale)` after `findAndModify` solely to update `Sale.status`, which could overwrite concurrent `Sale` updates instead of using `Update.set("status", nextStatus)`.

---

## 5. Concurrency & Idempotency Review

- **Atomic filter**: `SaleController.java:259-270` uses `$expr` `$lte` on `($ifNull($refundedTotal, 0.0) + $ifNull($pendingRefundTotal, 0.0) + totalRefund) <= ($grandTotal + 0.001)`, scoped to `_id` and authenticated `storeId`.
- **Idempotency**: [ReturnRecord.java:16](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/model/ReturnRecord.java#L16) defines `storeId_idempotencyKey_partial_idx` (`unique = true, partialFilter = "{'idempotencyKey': {'$type': 'string'}}"`). Duplicate keys return the existing `ReturnRecord` deterministically (`lines 218-223`, `308-313`). Note: `idempotencyKey` is optional; callers that omit it are not deduplicated.
- **Settlement finality**: `/confirm`, `/fail`, and `/reject` all filter on `status = "PENDING"`, preventing finalized records from transitioning again or double-updating `refundedTotal`/`pendingRefundTotal`.
- **Concurrency test evidence**: `DashboardStage2_2Test.phaseBC_refundLifecycleIdempotencyAndConcurrency` (`lines 281-297`) executes 2 concurrent `400.0` requests against `500.0` remaining capacity and asserts `(200, 400)` or `(400, 200)`. Concurrent `/confirm` calls on the same `id` are not explicitly tested.

---

## 6. Return-Aware COGS Verification

Source: [DashboardService.java:250-297](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/service/DashboardService.java#L250-L297)

- Formula verified: `Net COGS = sum((saleItem.quantity - min(saleItem.quantity, restockedQty)) * saleItem.costPrice)`.
- Non-restocked items (`restocked = false`) do **not** reverse COGS.
- Missing `SaleItem.costPrice`, missing `ReturnRecord.items` on a `COMPLETED` return, or ambiguous multi-line `productId` without `saleItemId` marks `cogsAvailable = false` and sets `cogs`, `grossProfit`, and `netProfit` to `null`.
- **Scenario A Test Gap**: The exact standalone scenario *(Sale ₹1,000, Cost ₹600 -> Full refund `COMPLETED` + all returned goods restocked -> Net Sales = ₹0, Net COGS = ₹0, Gross Profit = ₹0)* **does not exist as a standalone test** in `DashboardStage2_2Test.phaseD_returnAwareHistoricalCogs` (`lines 302-382`), which instead tests Scenario B (1 of 2 restocked -> COGS `300`), followed by Scenario C (2nd unit `restocked = false` -> COGS stays `300`), and Scenario D (missing items -> `cogsAvailable = false`).

---

## 7. Migration & Index Safety

Source: [DataSeeder.java:220-235](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/config/DataSeeder.java#L220-L235), [ReturnRecord.java:14-17](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/model/ReturnRecord.java#L14-L17)

- `DataSeeder.migrateReturnIdempotencyIndex()` checks `indexOps.getIndexInfo()` for `storeId_idempotencyKey_idx` and drops it idempotently.
- The new partial unique index uses a distinct name (`storeId_idempotencyKey_partial_idx`), so `auto-index-creation=true` on startup does not conflict with the old index name before `CommandLineRunner` runs.
- Store-scoped (`storeId`, `idempotencyKey`), safe across repeated startups, and no production index/data was touched during this audit. Startup creation of `storeId_idempotencyKey_partial_idx` would only fail if an existing collection already contained duplicate non-null `(storeId, idempotencyKey)` pairs.

---

## 8. AOV Semantics & UI Verification

Source: [DashboardService.java:137-143, 214-239](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/service/DashboardService.java#L137-L143), [DashboardMetricsResponse.java:12-16](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/dto/DashboardMetricsResponse.java#L12-L16), [dashboard.html:60-66, 388-392](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/frontend/dashboard.html#L60-L66)

- `DashboardService` implements `AOV = Net Sales / Eligible Order Count` (non-`CANCELLED`, non-`VOIDED`, including `REFUNDED`), and populates `fullyRefundedOrders`, `partiallyRefundedOrders`, and `aovPopulationLabel`.
- **[LOW / UI Gap]**: `frontend/dashboard.html` displays `#kpi-today-orders` and `#kpi-today-aov` (`AOV: ₹...`), but **does not render `aovPopulationLabel`, `fullyRefundedOrders`, or `partiallyRefundedOrders`** in the UI.

---

## 9. Timezone & Legacy Data Review

Source: [Sale.java:54-58](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/model/Sale.java#L54-L58), [SaleController.java:120-127](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/controller/SaleController.java#L120-L127), [SaleRepository.java:26-27](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/repository/SaleRepository.java#L26-L27), [DashboardService.java:375-384](file:///c:/Users/Siddheshwar/Desktop/grocery/grocery_management_system/backend/src/main/java/com/grocery/service/DashboardService.java#L375-L384)

- New sales store `Instant createdAtInstant` (unambiguous UTC instant) and IST `LocalDateTime createdAt`.
- `findByStoreIdAndIstRange` queries `createdAtInstant` `[startInstant, endInstant)` with fallback `createdAtInstant == null && createdAt >= startLocal && createdAt < endLocal`.
- Historical timestamps were not altered. Note: legacy records with `createdAtInstant == null` still rely on `LocalDateTime` MongoDB conversion using JVM default timezone; new records with `createdAtInstant` are 100% JVM-timezone-invariant.

---

## 10. Test Coverage Matrix

| Scenario | Classification | Evidence |
|---|---|---|
| `costPrice` tampering (`1.0`, `0.0`, `99999.0`) | **TESTED AND ASSERTED** | `DashboardStage2_2Test.phaseA_serverAuthoritativeCostSnapshot` |
| Product tenant isolation & `INACTIVE` rejection | **TESTED AND ASSERTED** | `DashboardStage2_2Test.phaseA_serverAuthoritativeCostSnapshot` |
| Return UI payload contract (`returns.html` -> `dashboard.html`) | **TESTED AND ASSERTED** | `dashboard-e2e.spec.js` (`Stage 2.2 UI-to-API-to-Dashboard`) |
| Pending refunds excluded from Net Sales | **TESTED AND ASSERTED** | `DashboardStage2_2Test.phaseBC...`, `dashboard-e2e.spec.js` |
| Confirmed (`COMPLETED`) refunds deducted | **TESTED AND ASSERTED** | `DashboardStage2_2Test.phaseBC...`, `dashboard-e2e.spec.js` |
| Failed refunds (`/fail`) | **NOT TESTED** | No test calls `POST /api/v1/returns/{id}/fail` (only seeded `FAILED` DB doc in `DashboardStage2Test`) |
| Rejected refunds (`/reject`) | **NOT TESTED** | No test calls `POST /api/v1/returns/{id}/reject` (only seeded `REJECTED` DB doc in `DashboardStage2Test`) |
| Idempotency (`idempotencyKey` + no double-restock) | **TESTED AND ASSERTED** | `DashboardStage2_2Test.phaseBC_refundLifecycleIdempotencyAndConcurrency` |
| Concurrent over-refund attempts | **TESTED AND ASSERTED** | `DashboardStage2_2Test.phaseBC_refundLifecycleIdempotencyAndConcurrency` |
| Restocked returned goods COGS reversal | **TESTED AND ASSERTED** | `DashboardStage2_2Test.phaseD_returnAwareHistoricalCogs` (Scenario B) |
| Non-restocked goods COGS retention | **TESTED AND ASSERTED** | `DashboardStage2_2Test.phaseD_returnAwareHistoricalCogs` (Scenario C) |
| Missing item-level return details -> COGS unavailable | **TESTED AND ASSERTED** | `DashboardStage2_2Test.phaseD_returnAwareHistoricalCogs` (Scenario D) |
| Full refund + full restock COGS reversal (Scenario A: ₹1000/₹600 -> ₹0/₹0/₹0) | **NOT TESTED** | Omitted from `DashboardStage2_2Test.phaseD` |
| Timezone invariance (`UTC` vs `Asia/Kolkata`) | **TESTED AND ASSERTED** | `DashboardStage2_2Test.phaseEF_aovBreakdownAndTimezoneInvariance` |
| Invalid custom period (`endDate < startDate` -> `400`) | **TESTED AND ASSERTED** | `DashboardStage2_2Test.phaseEF_aovBreakdownAndTimezoneInvariance` |
| Dashboard API recovery (`#dashboard-retry-btn`) | **TESTED AND ASSERTED** | `dashboard-e2e.spec.js` (`DASH-UI-01..09`) |
| Cross-store isolation | **TESTED AND ASSERTED** | `DashboardStage2Test`, `MultiTenantIsolationTest`, `dashboard-e2e.spec.js`, `phase6-full-e2e.spec.js` |
| XSS safety & Responsive layout (`1440`, `768`, `375`) | **TESTED AND ASSERTED** | `dashboard-e2e.spec.js` (`DASH-UI-01..12`) |

---

## 11. Remaining Risks & Final Acceptance Decision

- **MEDIUM — Non-`DuplicateKeyException` stranding**: `SaleController.processReturn` does not compensate `pendingRefundTotal` if `returnRepository.save` fails with a non-duplicate error, and no background reconciliation repairs crash-interrupted `/confirm` updates.
- **MEDIUM — Missing Scenario A & `/fail`/`/reject` endpoint tests**: Standalone full-refund/full-restock COGS reversal (`Net Sales=0, COGS=0, Gross Profit=0`) and `/fail` / `/reject` transitions lack automated tests.
- **LOW — UI AOV population visibility**: `dashboard.html` does not display `aovPopulationLabel` or `fullyRefundedOrders` / `partiallyRefundedOrders`.
- **LOW — Optional `idempotencyKey`**: Direct API callers omitting `idempotencyKey` are not deduplicated.

**Final Decision: `CONDITIONALLY ACCEPTED`** — Core Stage 2.2 financial integrity, server-authoritative cost snapshots, return UI-to-backend contract, atomic over-refund reservation, return-aware COGS, and `Asia/Kolkata` timezone handling are verified on `grocery_db_test` (`37/37` Maven, `18/18` Playwright), with the above non-critical recovery, UI-label, and test-coverage items documented for follow-up.
