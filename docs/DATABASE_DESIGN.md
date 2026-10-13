# MongoDB Database Design Specification

## Overview
Database Name: `grocery_db`  
Technology: MongoDB v6.0+ via Spring Data MongoDB  
Host: `localhost:27017`

---

## Collections & Index Strategy

### 1. `users`
- Stores system credentials, roles, and status.
- **Indexes:**
  - `username` (Unique, Ascending)
  - `email` (Unique, Ascending)
  - `role` (Ascending)

### 2. `categories`
- Master categories for merchandise.
- **Indexes:**
  - `name` (Unique, Ascending)
  - `status` (Ascending)

### 3. `products`
- Master product catalog, SKU, barcode, unit, pricing, and stock counter.
- **Indexes:**
  - `sku` (Unique, Ascending)
  - `barcode` (Sparse, Ascending)
  - `categoryId` (Ascending)
  - Text search index on `name` and `brand`

### 4. `inventory_batches`
- Physical inventory batches received from suppliers with expiry dates and cost basis.
- **Indexes:**
  - Compound: `{ productId: 1, expiryDate: 1 }`
  - `batchNumber` (Ascending)
  - `status` (Ascending)

### 5. `inventory_transactions`
- Immutable ledger tracking every stock increment, decrement, and reason.
- Types: `OPENING_STOCK`, `PURCHASE_RECEIPT`, `SALE`, `CUSTOMER_RETURN`, `SUPPLIER_RETURN`, `DAMAGE`, `EXPIRY`, `ADJUSTMENT`.
- **Indexes:**
  - Compound: `{ productId: 1, createdAt: -1 }`
  - Compound: `{ referenceType: 1, referenceId: 1 }`

### 6. `suppliers`
- Supplier directory with tax ID and contact details.
- **Indexes:**
  - `name` (Ascending)
  - `phone` (Ascending)

### 7. `purchase_orders`
- Procurement orders lifecycle (`DRAFT`, `ORDERED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `CANCELLED`).
- **Indexes:**
  - `orderNumber` (Unique, Ascending)
  - `supplierId` (Ascending)
  - `status` (Ascending)

### 8. `goods_receipts`
- Physical goods inwards notes tied to purchase orders.
- **Indexes:**
  - `receiptNumber` (Unique, Ascending)
  - `purchaseOrderId` (Ascending)

### 9. `sales`
- Customer POS receipts with item snapshots (historical prices and costs).
- **Indexes:**
  - `receiptNumber` (Unique, Ascending)
  - `createdAt` (Descending)
  - `cashierId` (Ascending)
  - `customerId` (Ascending, Sparse)

### 10. `payments`
- Financial tender records (Cash, Card, UPI).
- **Indexes:**
  - `saleId` (Ascending)
  - `receiptNumber` (Ascending)

### 11. `returns`
- Return authorization and refund records.
- **Indexes:**
  - `returnNumber` (Unique, Ascending)
  - `saleId` (Ascending)

### 12. `customers`
- Customer loyalty records and contact information.
- **Indexes:**
  - `phone` (Unique, Sparse)

### 13. `expenses`
- Overhead operating expenses (Rent, Salaries, Electricity, etc.).
- **Indexes:**
  - `expenseDate` (Descending)
  - `category` (Ascending)

### 14. `notifications`
- Low stock and expiry warnings.
- **Indexes:**
  - Compound: `{ isRead: 1, createdAt: -1 }`

### 15. `store_settings`
- Store identity, currency symbol (`₹`), default tax rates, and receipt formatting.
