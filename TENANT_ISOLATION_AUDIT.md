# Multi-Tenant Data Isolation Audit (`TENANT_ISOLATION_AUDIT.md`)

## 1. Previous Architecture
- `User`, `Product`, `Category`, and `StoreSettings` had `storeId`, but only `ProductController` and `UserController` enforced tenant scoping.
- `Sale`, `ReturnRecord`, `Customer`, `Supplier`, `PurchaseOrder`, `Expense`, `InventoryBatch`, `InventoryTransaction`, and `Notification` lacked `storeId` fields, causing global `.findAll()` / `.findById()` queries across tenants.

## 2. Identified Security Gaps
- **CRITICAL**: Cross-tenant data exposure and IDOR on `GET`, `POST`, `PUT`, `PATCH`, and `DELETE` across Sales, Returns, Customers, Suppliers, Purchase Orders, Expenses, Inventory Batches/Transactions, Notifications, Categories, Settings, and Reports.
- **HIGH**: Cross-tenant relationship spoofing (e.g., Store B referencing Store A's `productId`, `customerId`, `supplierId`, or `saleId`).
- **HIGH**: Global `ReportController` aggregations (`/dashboard`, `/sales`, `/profit-loss`, `/export/{type}`) leaked revenue, expenses, and CSV exports across all stores.

## 3. Tenant Ownership Architecture
- Created `com.grocery.security.TenantContext`:
  - `requireStoreId(UserDetails)` derives `storeId` strictly from the authenticated server-side `UserPrincipal` (`SecurityContextHolder`), throwing `AccessDeniedException` if missing.
  - Client-supplied `storeId` fields in request bodies are never trusted and are overwritten with the authenticated `storeId`.

## 4. Tenant Ownership Matrix

| Model | `storeId` Field | Repository Isolation | Controller / Service Isolation | Status |
|---|---|---|---|---|
| `User` | Yes (`storeId`, `storeCode`) | `findByStoreCode`, `findByStoreId` | `UserController` (`isSameTenant`) | **Isolated** |
| `Product` | Yes (`storeId`, compound `(storeId, sku)`) | `findByStoreId*`, `findLowStockProductsByStore` | `ProductController`, `InventoryController` | **Isolated** |
| `Category` | Yes (`storeId`) | `findByStoreId`, `findByStoreIdAndId` | `CategoryController` | **Isolated** |
| `StoreSettings` | Yes (`storeId`, `STORE_SETTINGS_<storeId>`) | `findById` scoped key | `SettingsController` | **Isolated** |
| `Sale` | Added (`@Indexed storeId`) | `findByStoreId*`, `findByStoreIdAndId` | `SaleController` + product/customer ownership check | **Isolated** |
| `ReturnRecord` | Added (`@Indexed storeId`) | `findByStoreIdOrderByCreatedAtDesc`, `findByStoreIdAndId` | `SaleController` + sale/product ownership check | **Isolated** |
| `Customer` | Added (`@Indexed storeId`) | `findByStoreId*`, `findByStoreIdAndId`, `findByStoreIdAndPhone` | `CustomerController` | **Isolated** |
| `Supplier` | Added (`@Indexed storeId`) | `findByStoreId*`, `findByStoreIdAndId`, `existsByStoreIdAndCode` | `SupplierController` | **Isolated** |
| `PurchaseOrder` | Added (`@Indexed storeId`) | `findByStoreId*`, `findByStoreIdAndId` | `PurchaseController` + supplier/product ownership check | **Isolated** |
| `Expense` | Added (`@Indexed storeId`) | `findByStoreIdOrderByExpenseDateDesc`, `findByStoreIdAndId` | `ExpenseController` | **Isolated** |
| `InventoryBatch` | Added (`@Indexed storeId`) | `findByStoreId*` | `InventoryController`, `PurchaseController`, `ReportController` | **Isolated** |
| `InventoryTransaction` | Added (`@Indexed storeId`) | `findTop50ByStoreIdOrderByCreatedAtDesc` | `InventoryController`, `SaleController`, `PurchaseController` | **Isolated** |
| `Notification` | Added (`@Indexed storeId`) | `findByStoreId*`, `countByStoreIdAndRead` | `NotificationController`, `SaleController`, `InventoryController` | **Isolated** |

## 5. Aggregations & Reports Updated
- `ReportController` (`/dashboard`, `/sales`, `/profit-loss`, `/export/{type}`): All queries (`Sale`, `Product`, `InventoryBatch`, `Expense`) are filtered by `tenantContext.requireStoreId(userDetails)` before summing revenue, discounts, taxes, expenses, low-stock counts, expiring batches, and generating CSV exports.

## 6. Data Migration Strategy & Findings
- Implemented `TenantMigrationService#runMigration(boolean dryRun)`:
  - Idempotent, non-destructive, never overwrites non-blank `storeId`, and supports `dryRun=true`.
  - Infers `storeId` strictly from verified parent/child links (`Sale` <- `Product`/`User`, `ReturnRecord` <- `Sale`/`Product`, `InventoryBatch` <- `Product`, `InventoryTransaction` <- `Product`, `PurchaseOrder` <- `Supplier`/`Product`).
  - Leaves standalone unowned records (`Customer`, `Supplier`, `Expense`, `Notification`, or unlinked `Sale`/`PurchaseOrder`) untouched and reports their IDs under `unresolvedIds` for manual review.
- `DataSeeder.seedCustomers(defaultStore)` now tags newly seeded starter customers with `defaultStore.getId()`.

## 7. API Contract & Index Changes (Phase 5.1)
- **Customer & Supplier Compound Unique Indexes**:
  - `Customer`: Replaced global `unique = true` on `phone` with compound unique index `storeId_phone_unique_idx` (`{'storeId': 1, 'phone': 1}`).
  - `Supplier`: Replaced global `unique = true` on `code` with compound unique index `storeId_code_unique_idx` (`{'storeId': 1, 'code': 1}`).
  - `DataSeeder` (`migrateCustomerPhoneIndex`, `migrateSupplierCodeIndex`) safely checks for missing `storeId` or intra-store duplicates before dropping the legacy global `phone`/`code` index and ensuring the compound unique index.
- **Query Performance Compound Indexes**:
  - `Sale`: `storeId_createdAt_idx`, `storeId_receiptNumber_idx`
  - `ReturnRecord`: `storeId_createdAt_idx`
  - `PurchaseOrder`: `storeId_status_createdAt_idx`
  - `Expense`: `storeId_expenseDate_idx`
  - `InventoryBatch`: `storeId_expiry_status_idx`
  - `InventoryTransaction`: `storeId_createdAt_idx`
  - `Notification`: `storeId_read_createdAt_idx`
- **Additional Phase 5.1 Fixes**:
  - `ProductController.createProduct` / `updateProduct` now validate `categoryId` via `categoryRepository.findByStoreIdAndId(storeId, categoryId)` (`400` on cross-tenant category).
  - `UserController.getAllUsers` fails securely (`403`) if `storeCode` is missing instead of falling back to `findAll()`.
  - `TenantMigrationService` detects contradictory multi-store relationships (`conflictingIds`), leaves conflicting records untouched, and reports exact `inspectedCount`, `eligibleCount`, `migratedCount`, `skippedCount`, `unresolvedCount`, and `conflictingCount`.

## 8. Test Scenarios & Results
- Verified in `MultiTenantIsolationTest` (`grocery_db_test`):
  - Two full stores (Store A & Store B), each with `ADMIN`, `STORE_MANAGER`, `CASHIER`, `INVENTORY_STAFF`.
  - Cross-tenant negative tests + own-tenant positive tests across all 9 newly isolated domains, reports, CSV exports, spoofed `storeId` payloads, relationship validation (including cross-tenant `categoryId` on `Product`), compound unique indexes (`Customer.phone` & `Supplier.code` across stores vs within same store), unauthenticated `401` checks, and `TenantMigrationService` dry-run/execute/idempotency/conflict detection.
- Remaining risks: Existing `localStorage` JWT storage; standalone MongoDB without multi-document ACID transactions; pre-existing live `:8080` server needs restart to serve the updated backend build for Playwright E2E.
