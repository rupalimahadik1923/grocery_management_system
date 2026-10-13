# TASKS.md

## Phase 0 — Project Setup

- [x] Create project folder/repository
- [x] Add documentation files
- [x] Install Java 17 & Maven
- [x] Install & start MongoDB (localhost:27017)
- [x] Initialize Git repository (`master`)
- [x] Spring Boot 3.2.5 enterprise REST application structure
- [x] Connect Spring Data to MongoDB (`grocery_db`)
- [x] Add `.env.example` & `.gitignore`

## Phase 1 — Database & Persistence

- [x] Design MongoDB document collections & indexes (`DATABASE_DESIGN.md`)
- [x] Create document models (Product, Category, Batch, Transaction, Sale, Customer, PO, Expense, Settings, User)
- [x] Create seed data runner (`DataSeeder.java` with users, categories, products, customer loyalty, settings)
- [x] Compound indexes on SKU, barcodes, batches FEFO dates

## Phase 2 — Authentication & Security

- [x] User document & repository
- [x] BCrypt password hashing & Spring Security filter chain
- [x] JWT Token generation & authentication filter
- [x] Login endpoint (`POST /api/v1/auth/login`)
- [x] Me endpoint (`GET /api/v1/auth/me`)
- [x] Role checks (ADMIN, STORE_MANAGER, CASHIER, INVENTORY_STAFF)
- [x] Automated auth test suite passing (`AuthControllerTest.java` - 4/4 passing)

## Phase 3 — Product & Category Management

- [x] Category CRUD & display order (`CategoryController.java` & `categories.html`)
- [x] Product master catalog CRUD (`ProductController.java` & `products.html`)
- [x] Instant search by SKU, Barcode, and Name
- [x] Category filtering & status toggling (Active / Inactive)
- [x] CSV Catalog Export

## Phase 4 — Inventory & Batch Tracking (FEFO)

- [x] Real-time stock balance tracking (`inventory.html`)
- [x] Immutable transaction audit ledger (`InventoryTransaction.java`)
- [x] FEFO Batch expiry tracking (`InventoryBatch.java`)
- [x] Low-stock automated threshold alerts
- [x] Physical stock adjustments modal & audit trail

## Phase 5 — Customer Management & Loyalty

- [x] Customer registration & edit (`customers.html`)
- [x] Real-time customer phone lookup for POS
- [x] Automated loyalty points calculation (1 pt / ₹100 spent)
- [x] Loyalty tiers (Bronze, Silver, Gold)

## Phase 6 — Point of Sale (POS) Checkout

- [x] High-speed fast scan / search bar (`pos.html`)
- [x] Category pills navigation & product card catalog
- [x] Cart line items with qty counters & trash removal
- [x] Subtotal, discount %, CGST (2.5%), SGST (2.5%), grand total calculations
- [x] Atomic POS checkout (`POST /api/v1/sales`)
- [x] Real-time stock deduction & ledger transaction logging
- [x] Payment tender modal (Cash with change due calculator, UPI QR, Card)
- [x] 80mm thermal receipt preview & print generator (`window.print()`)

## Phase 7 — Sales History, Procurement & Expenses

- [x] Sales history explorer (`sales.html`) with receipt drilldown & reprint
- [x] Customer returns & restocking terminal (`returns.html`)
- [x] Supplier directory (`suppliers.html`)
- [x] Purchase orders procurement lifecycle (`purchases.html`: Draft -> Ordered -> Goods Inward)
- [x] Operating expenses ledger (`expenses.html`)
- [x] Executive P&L, sales summaries & CSV export downloads (`reports.html`)
- [x] Real-time alerts center (`notifications.html`)
- [x] Staff user management & role control (`users.html`)
- [x] Store profile & POS settings (`settings.html`)

## Phase 8 — UI/UX Design System

- [x] Shared sidebar navigation & topbar (`layout.js`)
- [x] Clean Fresh Emerald Green design tokens (`style.css`, `layout.css`, `pos.css`, `forms.css`, `tables.css`)
- [x] Responsive layout & empty states
- [x] Toast notifications & interactive modals

## Phase 9 — Testing & Verification

- [x] Automated Spring Boot backend test suite (`mvn test` BUILD SUCCESS)
- [x] Git initial baseline commit (`master`)

## Phase 10 — College Deliverables

- [ ] Project documentation report document
- [ ] PowerPoint PPT presentation
- [ ] ER / Architecture diagrams
- [ ] Live demo script and walkthrough guide
