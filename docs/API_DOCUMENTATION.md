# REST API Documentation (v1.0)

Base URL: `/api/v1`  
Data Format: `application/json`

---

## 1. Authentication (`/api/v1/auth`)
* `POST /api/v1/auth/login`
  * Body: `{ "username": "admin", "password": "password123" }`
  * Response: `{ "token": "jwt...", "username": "admin", "role": "ADMIN", "fullName": "Store Administrator" }`
* `GET /api/v1/auth/me`
  * Headers: `Authorization: Bearer <token>`
  * Response: Current user profile and granted roles.

## 2. Categories (`/api/v1/categories`)
* `GET /api/v1/categories` - List active categories with item counts.
* `POST /api/v1/categories` - Create new category.
* `PUT /api/v1/categories/{id}` - Update category.
* `DELETE /api/v1/categories/{id}` - Delete if no associated products exist.

## 3. Products (`/api/v1/products`)
* `GET /api/v1/products` - Filtered & paginated product catalog.
* `GET /api/v1/products/{id}` - Product details with active batches.
* `GET /api/v1/products/search?q={query}` - Instant search by name, SKU, or barcode.
* `POST /api/v1/products` - Create new product with unique SKU.
* `PUT /api/v1/products/{id}` - Update pricing, unit, and thresholds.
* `PATCH /api/v1/products/{id}/status` - Soft activate/deactivate.

## 4. Inventory (`/api/v1/inventory`)
* `GET /api/v1/inventory` - Current stock balances across all products.
* `GET /api/v1/inventory/low-stock` - Products below reorder threshold.
* `GET /api/v1/inventory/expiring` - Batches expiring within configured window.
* `GET /api/v1/inventory/transactions` - Auditable stock movement ledger.
* `POST /api/v1/inventory/adjustments` - Stock adjustments (Damage, Expiry, Physical Count).

## 5. Suppliers & Purchases (`/api/v1/purchases`, `/api/v1/suppliers`)
* `GET /api/v1/suppliers` - Supplier directory.
* `POST /api/v1/suppliers` - Add supplier.
* `GET /api/v1/purchases` - Purchase order list with fulfillment statuses.
* `POST /api/v1/purchases` - Create new Purchase Order (`DRAFT`).
* `POST /api/v1/purchases/{id}/submit` - Confirm and order goods (`ORDERED`).
* `POST /api/v1/purchases/{id}/receive` - Inward delivery, record batches & update stock.

## 6. Point of Sale & Sales (`/api/v1/sales`)
* `POST /api/v1/sales` - Process checkout atomically, deduct stock, and return receipt.
* `GET /api/v1/sales` - Sales history with date, cashier, and receipt filters.
* `GET /api/v1/sales/{id}` - Sale order drilldown with line items.
* `POST /api/v1/returns` - Process partial or complete customer returns and refunds.

## 7. Reports & Analytics (`/api/v1/reports`)
* `GET /api/v1/reports/dashboard` - Real-time KPI summaries for dashboard.
* `GET /api/v1/reports/sales?from={date}&to={date}` - Periodic sales breakdown.
* `GET /api/v1/reports/profit-loss?from={date}&to={date}` - Revenue vs COGS vs Expenses.
* `GET /api/v1/reports/export/{type}?format=csv` - Direct CSV export download.
