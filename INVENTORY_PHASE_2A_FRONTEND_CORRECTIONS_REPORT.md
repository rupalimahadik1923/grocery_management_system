# INVENTORY MODULE — PHASE 2A: INVENTORY FRONTEND CORRECTIONS REPORT

**Project:** Grocery Management System  
**File Modified:** `frontend/inventory.html`  
**Date:** 2026-11-27  

---

## 1. Problems Discovered in `frontend/inventory.html`

1. **InventoryBatch Field Mismatches**:
   - `renderBatches()` read `b.remainingQuantity`, `b.initialQuantity`, and `b.costPricePerUnit`, whereas `InventoryBatch.java` serializes `currentQty`, `initialQty`, and `purchaseCost`.
   - Batches without an expiry date (`expiryDate: null`) were passed to `new Date(null)` (epoch `1970-01-01`), causing them to display as `EXPIRED`.
2. **InventoryTransaction Field Mismatches**:
   - `renderTransactions()` read `tx.transactionType`, `tx.referenceNumber`, and `tx.remarks`, whereas `InventoryTransaction.java` serializes `type`, `referenceId`, and `notes`.
   - Newer transaction types (`RESTOCK`, `PURCHASE_RECEIPT`, `OPENING_STOCK`, `EXPIRY`, `INTERNAL_USE`) lacked dedicated badge mappings.
3. **Broken Expiry Window Filter & Stale Initial KPI**:
   - `#expiry-window-select` had no `change` event listener and `renderBatches()` ignored the selected window.
   - `metric-expiring-count` stayed at `0` on initial page load until the user clicked the **Batch & Expiry Tracker** tab.
4. **Missing HTML Escaping & Client-Side Adjustment Guards**:
   - Dynamic strings (product names, SKUs, categories, batch numbers, supplier names, notes, toast messages) were interpolated into `innerHTML` without HTML escaping.
   - `adjForm` submit did not guard against `0` or non-finite quantities before calling `POST /api/v1/inventory/adjustments`.

---

## 2. Corrections Implemented

- **Fixed Batch Table Bindings (`renderBatches`)**:
  - Updated to `b.currentQty`, `b.initialQty`, and `b.purchaseCost`.
  - Added null-safe `expiryDate` handling: displays `-` and `<span class="badge badge-neutral">No Expiry</span>` when `expiryDate` is `null`.
  - Wired `#expiry-window-select` (`15`, `30`, `60`, `ALL`) with a `change` listener and filtering logic in `renderBatches()`.
- **Fixed Audit Ledger Bindings (`renderTransactions`)**:
  - Updated to `tx.type`, `tx.referenceId`, and `tx.notes`.
  - Added badge styles for `RESTOCK`, `PURCHASE_RECEIPT`, `OPENING_STOCK`, `SALE`, `CUSTOMER_RETURN`, `DAMAGE`, `EXPIRY`, `INTERNAL_USE`, and `ADJUSTMENT`.
- **Fixed Initial Expiring KPI (`loadData`)**:
  - Fetches `GET /api/v1/inventory/expiring?days=30` alongside `GET /api/v1/products` on page load so `metric-expiring-count` is immediately accurate.
- **Added `escapeHtml` & Adjustment Validation**:
  - Escaped all dynamic values rendered into `innerHTML` (stock table, batches table, transactions table, dropdowns, and toast messages).
  - Validated `productId` and `Number.isFinite(qty) && qty !== 0` on `adjForm` submission, and refreshed the audit ledger automatically if the `movements` tab is active.

---

## 3. Verification

- Extracted the `<script type="module">` block from `frontend/inventory.html` and validated ES module syntax with `node --input-type=module --check` (exited with code `0`, no syntax errors).
- Backend integration tests were not re-run because no backend Java files were modified in Phase 2A (previous backend suite passed 71/71 in Phase 1C).
- Full browser E2E verification of `inventory.html` is scheduled for **Phase 3**.

---

## 4. Next Phase

- **Phase 2B — Restock & Adjustment UI Workflow** (adding the Restock / Stock-In modal & row actions on `inventory.html` and `products.html`).
