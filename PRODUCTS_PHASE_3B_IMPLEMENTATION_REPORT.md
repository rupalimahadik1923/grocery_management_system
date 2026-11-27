# PRODUCTS — PHASE 3B: CATALOG DATA BINDING & EDIT INTEGRITY REPORT

**Project:** Grocery Management System  
**Workspace:** `C:\Users\Siddheshwar\Desktop\grocery\grocery_management_system`  
**Phase:** 3B — Catalog Data Binding & Edit Integrity  
**Execution Date:** 2026-11-26  
**Target Environment:** `grocery_db_test` (Isolated test database on `127.0.0.1:27017`)  
**Roles:** Senior Software Architect, Frontend Developer, Backend Engineer, QA Engineer  

---

## 1. EXECUTIVE SUMMARY

In response to the conditional approval and architectural requirements, Phase 3B focused on resolving product creation and editing data binding defects across the frontend (`frontend/products.html`) and backend (`ProductController.java`), introducing a dedicated `ProductUpdateRequest` DTO to permanently fix `AUD-04-UPDATE-01` (primitive default overwrites on partial updates).

In strict adherence to the safety rules:
1. **Isolated Opening Stock Scope**: No standalone `OPENING_STOCK` ledger insertion was implemented in this task. Opening-stock durability remains reserved for the separate stock-movement architecture review.
2. **Safe Update Semantics**: Replaced direct entity binding on `PUT /api/v1/products/{id}` with `ProductUpdateRequest` DTO, strictly preserving `id`, `storeId`, `currentStock`, and omitted fields.
3. **Server-Authoritative Category Resolution**: Verified category existence and tenant ownership in `CategoryRepository`, rejecting inactive or cross-store categories and authoritatively setting `categoryName`.
4. **Canonical SKU Normalization & Live Check**: SKU is normalized to uppercase and trimmed across frontend and backend. Live debounced duplicate checking was added to `products.html` with exclude-self support on edit (`/api/v1/products/check-sku?sku=...&excludeId=...`).
5. **Robust Numeric Parsing**: Eliminated `parseFloat(...) || 0`, enforcing explicit required validation, non-negative cost prices, and positive selling prices.

All **26 tests** in `ProductControllerTest` passed with 0 failures, 0 errors, and 0 skipped.

---

## 2. EXACT FILES CHANGED

1. **`backend/src/main/java/com/grocery/dto/ProductUpdateRequest.java`** (NEW):
   - Dedicated request DTO for partial product updates with boxed wrapper types (`Double costPrice`, `Double sellingPrice`, `Double minStockLevel`, `Double taxRate`) to prevent Jackson primitive zero-default overwrites.
2. **`backend/src/main/java/com/grocery/controller/ProductController.java`** (MODIFIED):
   - `checkSku`: Added `@RequestParam(required = false) String excludeId` to allow existing products to retain their SKU during editing without flagging a false duplicate.
   - `createProduct`: Added canonical uppercase SKU normalization, barcode whitespace-to-null sanitization, active category validation, server-authoritative `categoryName` derivation, and non-negative initial stock validation.
   - `updateProduct`: Converted to accept `ProductUpdateRequest`. Blocks editing archived products via PUT. Strictly preserves existing `currentStock`, `storeId`, and omitted attributes. Resolves category authoritatively or allows explicit category clearing with `""`. Blocks status tampering via PUT (archival must go through delete/archive).
3. **`frontend/products.html`** (MODIFIED):
   - Enhanced modal form with explicit Add vs. Edit mode separation.
   - In Edit mode: `prod-stock` is set to `readOnly = true` with a clear explanation: *"Stock changes must be made through Inventory Adjustments."*
   - In Add mode: `prod-stock` is editable and validated.
   - Added debounced real-time SKU uniqueness check with visual feedback (Available, In use, Checking, Error).
   - Added `parseNumericField(...)` throwing validation errors on empty, malformed, or out-of-range numbers.
   - Guarded table rendering against null/undefined numeric values with fallback formatting.
4. **`backend/src/test/java/com/grocery/ProductControllerTest.java`** (MODIFIED):
   - Added 5 new targeted integration tests covering Phase 3B requirements (bringing test suite total to 26 tests).

---

## 3. ROOT CAUSE CONFIRMATION & DEFECT RESOLUTION

| Defect Area | Confirmed Root Cause | Resolution Implemented |
|---|---|---|
| **Category Desynchronization** | `products.html` read `catSelect.options[selectedIndex].text`, which sent `"Select Category"` if options were not loaded. Backend `PUT` trusted client-provided `categoryName`. | Frontend passes `categoryId`. Backend resolves `Category` from `CategoryRepository` scoped to `storeId` and derives `categoryName` authoritatively. Rejects cross-store or inactive categories. Supports explicit clearing (`categoryId: ""`). |
| **SKU Normalization & Duplicate Collision** | SKU case was not normalized, and editing a product triggered false duplicate errors against itself. | Implemented canonical trim + uppercase on frontend and backend. Updated `/api/v1/products/check-sku` to support `excludeId`. Added debounced live check in modal. |
| **Numeric Field Parsing & Fallbacks** | `parseFloat(...) \|\| 0` silently coerced empty required fields into `0.0`. Table render called `.toFixed(2)` directly on potentially null prices. | Created `parseNumericField(...)` enforcing non-negative cost and positive selling price. Replaced table render with null-safe currency formatting. |
| **Stock Field on Product Edit** | Edit modal displayed editable stock, but backend `PUT` silently ignored `currentStock`, confusing users. | Form sets stock input to `readonly` in Edit mode with informational text. `PUT` endpoint strictly preserves existing `currentStock` and never mutates it. |
| **Entity Overwrite Data Loss (AUD-04-UPDATE-01)** | `PUT` bound incoming JSON directly to `Product` entity; omitted primitive fields defaulted to `0.0`. | Replaced entity binding with `ProductUpdateRequest` DTO using boxed wrapper types. Only present fields are updated. |

---

## 4. BACKEND VALIDATION & API CONTRACT DETAILS

### `/api/v1/products/check-sku`
- **Method:** `GET`
- **Parameters:**
  - `sku` (required, string): Auto-trimmed and uppercased.
  - `excludeId` (optional, string): Product ID to exclude from duplicate search (used when editing an existing product).
- **Response:** `{ "exists": boolean, "sku": string }`

### `POST /api/v1/products`
- **SKU Normalization:** Always saved as uppercase trimmed string (`cleanSku = product.getSku().trim().toUpperCase()`).
- **Barcode Sanitization:** If null, empty, or whitespace, normalized to `null`.
- **Category Validation:** Verified in `CategoryRepository` for authenticated `storeId`. Must be active. `categoryName` is set directly from the database record.
- **Initial Stock:** Validated as $\ge 0$.

### `PUT /api/v1/products/{id}`
- **Request Body:** `ProductUpdateRequest` DTO
- **Archival Protection:** Rejects update if existing product is `ARCHIVED` (400 Bad Request).
- **Stock Protection:** `existing.getCurrentStock()` is untouched.
- **Field Preservation:** If `costPrice`, `sellingPrice`, `minStockLevel`, `taxRate`, `description`, etc. are null in the DTO, the existing document values are preserved.
- **Status Safeguards:** Rejects arbitrary status values; blocks direct transition to `ARCHIVED` via PUT (must use delete/archive endpoint).

---

## 5. TEST EXECUTION EVIDENCE

### Test Suite Execution
- **Command:** `mvn test -Dtest=ProductControllerTest`
- **JDK:** Java 21.0.12.1 LTS
- **Database:** `grocery_db_test` (Local Standalone MongoDB v9.0.8)

```
[INFO] Running com.grocery.ProductControllerTest
...
[INFO] Tests run: 26, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 20.11 s -- in com.grocery.ProductControllerTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Specific Phase 3B Integration Tests Verified:
1. `shouldNormalizeSkuOnCreation`: Confirms SKU is trimmed and converted to uppercase upon creation.
2. `shouldNormalizeBlankBarcodeToNull`: Confirms blank/whitespace barcode values are persisted as `null` on both create and update.
3. `shouldValidateCategoryAndDeriveName`: Verifies server-side derivation of `categoryName`, rejects cross-store category IDs with HTTP 400, and verifies clean category clearing.
4. `shouldPreserveOmittedFieldsAndStockOnUpdate`: Verifies that sending a partial update changing only `sellingPrice` strictly preserves `costPrice`, `currentStock`, `minStockLevel`, `taxRate`, and `unit`.
5. `shouldProtectStatusTransitionsOnPut`: Verifies that PUT cannot directly set status to `ARCHIVED` and cannot modify an already archived product.
6. All 21 baseline tests (cross-tenant isolation, SKU uniqueness, safe archival lifecycle, status patch authorization) continue to pass.

---

## 6. REMAINING OPENING-STOCK LIMITATIONS

In accordance with Step 4 instructions, **no standalone `OPENING_STOCK` ledger insertion was implemented**.
- When creating a product with initial stock $> 0$, the product's `currentStock` is recorded in the catalog, but no `InventoryTransaction` is generated yet.
- Opening stock ledger durability and crash-consistency will be implemented strictly under the approved durable stock-movement architecture (Stage 3A.2).
- DataSeeder was not modified; historical starter records remain unchanged.

---

## 7. FINAL VERDICT

**PHASE 3B IMPLEMENTATION COMPLETE AND VERIFIED**

All product catalog data binding defects, category desynchronization, SKU normalization gaps, numeric validation fallbacks, and partial update data loss issues have been remediated with server-authoritative safeguards and verified by automated integration tests.
