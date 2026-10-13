# FreshGrocery POS & Inventory System — Stitch UI Generation Prompts

This document contains in-depth, production-ready prompts designed for **Stitch** (or any AI UI code generator) to generate all required frontend pages for the FreshGrocery store management system.

---

## 🎨 Global System Context for Stitch
*(Always include this preamble in your Stitch session so every page adheres to the project's design system)*

```text
You are generating a production-grade, high-density web UI page for "FreshGrocery POS & Enterprise Inventory Management".
TECHNICAL RULES:
- Output clean, valid HTML5 with Vanilla JavaScript ES6 modules.
- DO NOT use React, Vue, Angular, or TailwindCSS.
- Use the project's existing CSS files linked in the <head>:
  <link rel="stylesheet" href="css/style.css">
  <link rel="stylesheet" href="css/layout.css">
  <link rel="stylesheet" href="css/forms.css">
  <link rel="stylesheet" href="css/tables.css">
  <link rel="stylesheet" href="css/responsive.css">
  (And page-specific CSS like pos.css or dashboard.css where applicable)
- The page MUST follow the application shell layout:
  <body>
    <div class="app-container">
      <div id="sidebar-container"></div>
      <div class="main-wrapper">
        <div id="topbar-container"></div>
        <main class="content-body">
          <!-- Page UI Content -->
        </main>
      </div>
    </div>
    <script type="module">
      import { Layout } from './js/layout.js';
      import { Api } from './js/api.js';
      Layout.init({ activePage: '<PAGE_ID>', title: '<PAGE_TITLE>' });
      // Event listeners & interactive logic here
    </script>
  </body>
- Color theme: Fresh Emerald Green (#16a34a, #22c55e), Slate Dark (#0f172a), Light slate backgrounds (#f8fafc), White cards with subtle borders (#e2e8f0).
- Include rich interactive elements: filter toolbars, search bars, action modals with .modal-overlay and .modal-content, status badges, and sample realistic grocery data.
- Assign clear, descriptive DOM IDs to all input fields, buttons, tables, and modals for easy API connectivity.
```

---

## Page 1: Point of Sale Terminal (`pos.html`)

### Stitch Prompt:
```text
Create a high-speed, modern grocery store Point of Sale (POS) interface for the file "pos.html".

Design Theme & CSS:
- Link css/style.css, css/layout.css, css/pos.css, css/forms.css, css/responsive.css.
- Page title: "Point of Sale (POS) | FreshGrocery".
- Active page ID: 'pos'.

Layout Architecture (Two-Column Split):
1. LEFT COLUMN: Product Catalog & Fast-Scan Panel (.pos-catalog-panel):
   - Top Header Bar:
     - Barcode / Search Input (#pos-barcode-input): large input with barcode scanner icon, autofocus, placeholder "Scan barcode or search product (F2)...".
     - Shortcut hints: "[F2: Scan] [F4: Customer] [F8: Tender/Pay] [Esc: Clear]".
   - Category Pills Bar (.pos-category-pills):
     - Horizontal scrollable pills: "All Items", "Fresh Produce 🍎", "Dairy & Milk 🥛", "Bakery & Bread 🍞", "Beverages 🧃", "Snacks 🍿", "Household 🧼", "Spices 🌶️".
     - Active pill style with green accent.
   - Fast Product Grid (.pos-product-grid):
     - Product cards (.pos-product-card) featuring: product emoji/icon, product name (e.g., "Amul Taaza Milk 500ml", "Farm Fresh Apples 1kg", "Brown Bread 400g"), SKU/barcode snippet, price (e.g., "₹32.00"), stock counter badge ("In Stock: 48"), and a quick "+ Add" button.
     - On click, product gets added to cart.

2. RIGHT COLUMN: Order Cart & Bill Tender Panel (.pos-cart-panel):
   - Cart Header:
     - "Current Order" title + order ticket # (e.g., "#ORD-8942").
     - Quick Customer Selector (#cart-customer-btn): displays "Walk-in Customer" with quick toggle to enter Customer Name/Phone for loyalty points.
     - "Clear Order" trash button.
   - Cart Items List (#pos-cart-list):
     - Scrollable list of cart items.
     - Each line item has: Item name, Unit price, Quantity counter (- [qty] +), Line total, and delete button (×).
     - Empty cart state with nice graphic if no items.
   - Cart Summary Box:
     - Subtotal (₹), Discount (%) input field, CGST (2.5%), SGST (2.5%), Grand Total with bold large font.
   - Action Tender Buttons:
     - "Hold Order" button, "Apply Coupon" button.
     - Large Prominent Green "Pay & Checkout (F8) - ₹XXX.XX" button (#btn-open-tender).

3. PAYMENT TENDER MODAL (#tender-modal):
   - Overlay modal (.modal-overlay) displaying:
     - Grand Payable Total highlighted.
     - Payment Method Tabs: "💵 Cash", "📱 UPI / QR Code", "💳 Card", "🎁 Split".
     - For Cash: Quick amount pills (Exact, ₹100, ₹200, ₹500, ₹2000), "Tendered Amount" input, and auto-calculated "Change Due".
     - For UPI: Dynamic QR Code mock box with "Scan with GPay/PhonePe/Paytm".
     - "Complete Transaction & Print Receipt" button (#btn-complete-payment).

4. THERMAL RECEIPT MODAL / PRINT PREVIEW (#receipt-modal):
   - 80mm thermal receipt styled preview with store header, receipt #, cashier name, date/time, itemized table, tax breakdown, footer thank-you message, and "🖨️ Print Receipt" button.

Include realistic dummy items in the cart by default so the preview looks stunning right away!
```

---

## Page 2: Product Management & Catalog (`products.html`)

### Stitch Prompt:
```text
Create a comprehensive, enterprise grocery Product Management & Catalog page for "products.html".

Design Theme & CSS:
- Link css/style.css, css/layout.css, css/tables.css, css/forms.css, css/responsive.css.
- Page title: "Products | FreshGrocery".
- Active page ID: 'products'.

Layout & Components:
1. KPI Stat Cards (4-column grid):
   - Total Products: 1,420
   - Active In-Stock: 1,350
   - Low Stock (< 10 units): 24
   - Out of Stock: 6

2. Filter & Action Toolbar:
   - Search bar (#product-search-input): "Search by name, SKU, or barcode..."
   - Category dropdown filter (#filter-category): All, Dairy, Produce, Bakery, Beverages, Snacks, Personal Care.
   - Status filter (#filter-status): All, Active, Inactive, Low Stock.
   - Sort dropdown: Name, Price (Low-High), Stock (Low-High), Recently Added.
   - Action buttons:
     - "📥 Export CSV" button.
     - "➕ Add New Product" button (#btn-add-product) in primary brand green.

3. Master Products Data Table (#products-table):
   - Columns:
     - Checkbox
     - Product Info (Image/Icon, Name, SKU, Barcode)
     - Category (Pill badge e.g., Dairy, Produce)
     - Unit (kg, pcs, pack, liter)
     - Cost Price (₹)
     - Selling Price (₹)
     - Current Stock (with stock warning badge if low)
     - Status (Active / Inactive toggle badge)
     - Actions (Edit ✏️, View Batches 📋, Deactivate 🚫)
   - Pagination controls at bottom with page numbers (1, 2, 3... 15) and "Showing 1-20 of 1,420 items".

4. "ADD / EDIT PRODUCT" MODAL (#product-modal):
   - Form fields in two columns:
     - Product Name (#prod-name) [Text, Required]
     - SKU (#prod-sku) [Text, e.g., PROD-MILK-001]
     - Barcode (#prod-barcode) [Text, e.g., 8901234567890]
     - Category (#prod-category) [Dropdown]
     - Unit of Measure (#prod-unit) [Dropdown: PCS, KG, G, LITER, PACK]
     - Purchase / Cost Price (#prod-cost) [Number]
     - Selling / Retail Price (#prod-price) [Number]
     - Min Reorder Level (#prod-min-stock) [Number, e.g., 15]
     - Tax Rate / GST (%) (#prod-tax) [Dropdown: 0%, 5%, 12%, 18%]
     - Description (#prod-desc) [Textarea]
     - Status Toggle: Active/Inactive
   - Modal footer with "Cancel" and "Save Product" button.

Include 6-8 realistic grocery sample products (Amul Butter 500g, Tata Salt 1kg, Britannia Bread, Aashirvaad Atta 5kg, Coca-Cola 750ml, Fortune Oil 1L) with realistic prices and stock levels.
```

---

## Page 3: Category Management (`categories.html`)

### Stitch Prompt:
```text
Create a clean, visual Category Management page for "categories.html".

Design Theme & CSS:
- Link css/style.css, css/layout.css, css/tables.css, css/forms.css, css/responsive.css.
- Page title: "Categories | FreshGrocery".
- Active page ID: 'categories'.

Layout & Components:
1. Page Header & Actions:
   - Header with title "Product Categories" and subtitle "Organize inventory hierarchy and POS navigation".
   - Search category input (#category-search).
   - "➕ Create Category" button (#btn-add-category).

2. Category Metric Highlights:
   - 3 stat cards: Total Categories (18), Total Assigned Products (1,420), Empty Categories (0).

3. Visual Category Grid & Table View:
   - Provide a Tab Switcher: "Card View 🗂️" / "Table View 📋".
   - In Card View: Grid of category cards.
     - Each card displays: Emoji icon (🥛, 🥦, 🍞, 🥤, 🧼, 🍿), Category Name, Description snippet, Badge with product count (e.g., "148 items"), status badge (Active), and action dropdown (Edit, Delete).
   - In Table View: Data table with columns: ID, Icon, Name, Slug, Product Count, Display Order, Status, Actions.

4. "ADD / EDIT CATEGORY" MODAL (#category-modal):
   - Category Name (#cat-name)
   - Emoji / Icon picker or input (#cat-icon)
   - Slug / URL identifier (#cat-slug)
   - Description (#cat-desc)
   - Sort / Display Order (#cat-order)
   - Status (Active / Hidden from POS)
   - Buttons: "Cancel" and "Save Category".

Provide sample categories: Dairy & Eggs, Fresh Vegetables, Fresh Fruits, Bakery & Snacks, Staples & Grains, Beverages, Personal Care, Household Cleaning.
```

---

## Page 4: Inventory & Batch Expiry Tracking (`inventory.html`)

### Stitch Prompt:
```text
Create an advanced Inventory Tracking, Batches & FEFO/FIFO Expiry Control page for "inventory.html".

Design Theme & CSS:
- Link css/style.css, css/layout.css, css/tables.css, css/forms.css, css/responsive.css.
- Page title: "Inventory & Batches | FreshGrocery".
- Active page ID: 'inventory'.

Layout & Components:
1. KPI Metrics Banner:
   - Total Stock Valuation (₹8,45,200)
   - Low Stock Alert Items (24 items)
   - Batches Expiring in < 30 Days (14 batches) - Rose alert pill
   - Damaged / Written-Off this month (₹3,400)

2. Multi-Tab Navigation:
   - Tab 1: "Stock Levels Overview" (#tab-stock)
   - Tab 2: "Batch & Expiry Tracker (FEFO)" (#tab-batches)
   - Tab 3: "Stock Movement Audit Ledger" (#tab-movements)

3. TAB 1: Stock Levels Overview:
   - Filter bar: Search item, category filter, threshold filter (All, Low Stock Only, Out of Stock).
   - "⚡ Quick Stock Adjustment" button (#btn-open-adjustment).
   - Table: SKU, Product Name, Category, In-Stock Qty, Min Threshold, Stock Health (Progress bar / status: Critical Red, Low Amber, Healthy Green), Actions (Adjust Stock, View Batches).

4. TAB 2: Batch & Expiry Tracker:
   - Table of active incoming batches:
     - Batch Number (e.g. BATCH-2024-098)
     - Product Name
     - Supplier Name
     - Inward Date
     - Expiry Date (with days remaining badge: e.g., "⚠️ 5 days left" in red, "22 days left" in amber)
     - Initial Qty vs Remaining Qty
     - Purchase Cost per unit
     - Action (Mark Waste / Discount Sale)

5. TAB 3: Stock Movement Ledger:
   - Auditable transaction log: Timestamp, Product Name, Transaction Type badge (PURCHASE_RECEIPT [Green], POS_SALE [Blue], RETURN [Purple], DAMAGE [Red], ADJUSTMENT [Gray]), Qty Change (+/-), Cashier/User, Reference PO/Receipt #.

6. "STOCK ADJUSTMENT MODAL" (#adjustment-modal):
   - Product selector dropdown
   - Adjustment Type: Physical Count Audit, Damaged Goods, Expired Waste, Internal Consumption
   - Adjustment Quantity (+ or -)
   - Reason / Remarks notes field
   - Recorded By staff user
   - Submit Adjustment button.
```

---

## Page 5: Purchase Orders & Inwarding (`purchases.html`)

### Stitch Prompt:
```text
Create a complete Procurement & Purchase Order lifecycle page for "purchases.html".

Design Theme & CSS:
- Link css/style.css, css/layout.css, css/tables.css, css/forms.css, css/responsive.css.
- Page title: "Purchases | FreshGrocery".
- Active page ID: 'purchases'.

Layout & Components:
1. Header & Quick Stats:
   - Title: "Purchase Orders & Goods Inward"
   - Metrics: Pending Deliveries (3), Total Month Inward (₹2,15,000), Draft POs (2).
   - Action Button: "➕ Create Purchase Order" (#btn-create-po).

2. Filter Toolbar:
   - Search by PO # or Supplier Name.
   - Status Tabs: "All Orders", "Draft 📝", "Ordered / In-Transit 🚚", "Received / Stocked ✅", "Cancelled ❌".
   - Date range selector.

3. Purchase Orders Table:
   - PO Number (e.g., PO-2024-104)
   - Supplier Name (e.g., "Amul Fresh Distributors")
   - Order Date
   - Expected Delivery Date
   - Items Count
   - Total Cost Amount (₹)
   - Status Badge (DRAFT, ORDERED, RECEIVED)
   - Actions:
     - If DRAFT: Edit, Send Order to Supplier
     - If ORDERED: "Inward Goods / Receive Stock" button (Opens Inward Modal)
     - If RECEIVED: View Goods Receipt Note (GRN)

4. "CREATE PURCHASE ORDER" DRAWER / MODAL (#po-modal):
   - Select Supplier (Dropdown with quick "+ Add Supplier" shortcut)
   - Expected Delivery Date picker
   - Line Items Table with dynamic rows:
     - Product selector
     - Order Qty
     - Unit Purchase Cost (₹)
     - Line Total (₹)
     - Remove row button (trash icon)
   - "+ Add Item Line" button
   - PO Total calculation, Remarks field, Save as Draft / Submit Order buttons.

5. "GOODS INWARD / RECEIVING MODAL" (#inward-modal):
   - PO # reference and Supplier name.
   - Line items verification:
     - For each item: Ordered Qty vs Received Qty input, Batch Number field, Manufacturing Date, Expiry Date.
   - Invoice / Delivery Challan # input.
   - "Confirm Delivery & Update Inventory" button.
```

---

## Page 6: Supplier Directory (`suppliers.html`)

### Stitch Prompt:
```text
Create a vendor and supplier management page for "suppliers.html".

Design Theme & CSS:
- Link css/style.css, css/layout.css, css/tables.css, css/forms.css, css/responsive.css.
- Page title: "Suppliers | FreshGrocery".
- Active page ID: 'suppliers'.

Layout & Components:
1. Header & Actions:
   - Page Header with "Supplier & Vendor Directory".
   - Search suppliers input by name, phone, or GSTIN.
   - "➕ Add New Supplier" button (#btn-add-supplier).

2. Supplier Summary Cards:
   - Active Suppliers: 28
   - Total Monthly Purchases: ₹4,60,000
   - Pending Supplier Dues / Payables: ₹78,500

3. Supplier Directory Table:
   - Supplier Code & Company Name (e.g., "SUP-01 - Metro Cash & Carry")
   - Contact Person (Name, Phone, Email)
   - GSTIN / Tax ID (e.g., 27AAAPL1234C1ZV)
   - Address / City
   - Payment Terms (e.g., Net 15, Net 30, COD)
   - Total Orders Count & Outstanding Balance
   - Status (Active / On Hold)
   - Actions: Create PO, View Order History, Edit, Delete.

4. "ADD / EDIT SUPPLIER" MODAL (#supplier-modal):
   - Company Name
   - Contact Person Name
   - Phone Number & Alternate Phone
   - Email Address
   - GSTIN / Tax ID Number
   - Billing & Warehouse Address
   - Bank Details (Account #, IFSC, Bank Name)
   - Payment Terms dropdown (Immediate, Net 7, Net 15, Net 30)
   - Save Supplier button.
```

---

## Page 7: Sales History & Receipt Drilldown (`sales.html`)

### Stitch Prompt:
```text
Create an audit-grade Sales History and POS Receipts Explorer page for "sales.html".

Design Theme & CSS:
- Link css/style.css, css/layout.css, css/tables.css, css/forms.css, css/responsive.css.
- Page title: "Sales History | FreshGrocery".
- Active page ID: 'sales'.

Layout & Components:
1. Sales Analytics Quick Ribbon:
   - Today's POS Sales: ₹42,850 (164 transactions)
   - Cash Tendered: ₹18,400 | UPI / QR: ₹20,150 | Cards: ₹4,300
   - Average Order Value: ₹261.28

2. Filters Bar:
   - Search by Receipt # (e.g., RCP-2024-8841) or Customer Phone.
   - Date range picker (Today, Yesterday, Last 7 Days, Custom range).
   - Cashier filter dropdown.
   - Payment method filter (All, Cash, UPI, Card).
   - "Export Sales Report CSV" button.

3. Sales Records Table:
   - Receipt Number
   - Date & Time
   - Cashier Name
   - Customer (Walk-in or Name/Phone)
   - Items Count
   - Payment Mode (Badge: Cash [Green], UPI [Blue], Card [Purple])
   - Total Amount (₹)
   - Status (Completed, Refunded, Partially Returned)
   - Actions: "View Receipt Details 👁️", "Reprint 🖨️", "Initiate Return 🔄".

4. "RECEIPT DRILLDOWN MODAL" (#receipt-detail-modal):
   - Detailed modal showing:
     - Store Header, Receipt #, Date, Terminal & Cashier.
     - Customer details.
     - Itemized list: Product Name, SKU, Qty, Unit Price, Discount, Total.
     - Tax Breakdown: CGST @ 2.5%, SGST @ 2.5%, Total Tax.
     - Tender breakdown: Mode of payment, Amount Paid, Change given.
     - Action buttons: "Print Invoice", "Download PDF", "Issue Return".
```

---

## Page 8: Returns & Refunds Terminal (`returns.html`)

### Stitch Prompt:
```text
Create an intuitive customer Returns, Restocking & Refunds page for "returns.html".

Design Theme & CSS:
- Link css/style.css, css/layout.css, css/tables.css, css/forms.css, css/responsive.css.
- Page title: "Returns & Refunds | FreshGrocery".
- Active page ID: 'returns'.

Layout & Components:
1. Two-Step Return Flow UI:
   - STEP 1: Receipt Lookup Bar (#return-lookup-box):
     - Large input: "Enter original receipt number (e.g. RCP-2024-8841) or scan barcode..."
     - "Lookup Receipt" button (#btn-lookup-receipt).
   - STEP 2: Return Processing Panel (Reveals once receipt is fetched):
     - Original order summary: Date, Cashier, Original Total, Payment method.
     - Table of original purchased items:
       - Checkbox to select item for return
       - Item Name & SKU
       - Purchased Qty
       - Return Qty input (max up to purchased qty)
       - Return Reason dropdown (Damaged/Defective, Expired, Wrong Item, Customer Regret)
       - Restock Checkbox ("Return to active inventory stock?")
       - Refund Subtotal per item.
   - Refund Tender & Summary:
     - Total Refund Amount (₹)
     - Refund Method dropdown (Original Payment Method, Cash, Store Credit / Loyalty Points)
     - Staff Notes / Approval notes
     - Prominent "Process Refund & Print Credit Note" button (#btn-process-refund).

2. Past Returns History Table:
   - Return Slip #, Original Receipt #, Date & Time, Items Returned, Refund Amount (₹), Reason, Processed By, Status.
```

---

## Page 9: Customers & Loyalty Directory (`customers.html`)

### Stitch Prompt:
```text
Create a Customer Relationship & Loyalty Program page for "customers.html".

Design Theme & CSS:
- Link css/style.css, css/layout.css, css/tables.css, css/forms.css, css/responsive.css.
- Page title: "Customers | FreshGrocery".
- Active page ID: 'customers'.

Layout & Components:
1. Header & Loyalty KPIs:
   - Registered Customers: 840
   - Active Loyalty Points Pool: 42,600 pts (Value: ₹4,260)
   - Repeat Customer Rate: 68%
   - "➕ Add New Customer" button (#btn-add-customer).

2. Search & Filter Bar:
   - Search by Customer Name or Mobile Phone Number.
   - Loyalty Tier filter: All, Bronze, Silver, Gold.

3. Customers Data Table:
   - Customer ID & Name
   - Mobile Phone & Email
   - Loyalty Tier badge (🥉 Bronze, 🥈 Silver, 🥇 Gold)
   - Points Balance
   - Total Orders Placed
   - Lifetime Spend (₹)
   - Last Visit Date
   - Actions: "View Profile & Orders 👤", "Edit ✏️", "Redeem Points 🎁".

4. "ADD / EDIT CUSTOMER" MODAL (#customer-modal):
   - Full Name
   - Phone Number (Required for POS loyalty lookup)
   - Email Address
   - Address / Area
   - Date of Birth / Anniversary (for promotional discounts)
   - Save Customer button.

5. "CUSTOMER HISTORY DRAWER" (#customer-drawer):
   - Slide-over panel displaying customer's lifetime spending summary, loyalty points earned/redeemed, and list of past POS receipts with clickable links.
```

---

## Page 10: Operating Expenses (`expenses.html`)

### Stitch Prompt:
```text
Create an Operating Expenses & Cash Outflow tracking page for "expenses.html".

Design Theme & CSS:
- Link css/style.css, css/layout.css, css/tables.css, css/forms.css, css/responsive.css.
- Page title: "Operating Expenses | FreshGrocery".
- Active page ID: 'expenses'.

Layout & Components:
1. Expense Stat Cards:
   - Total Expenses This Month: ₹64,200
   - Store Rent & Utilities: ₹35,000
   - Staff Salaries & Daily Wages: ₹22,000
   - Wastage & Loss: ₹7,200
   - "➕ Record New Expense" button (#btn-add-expense).

2. Filters Bar:
   - Date range selector (This Month, Last Month, Custom).
   - Category filter: Rent, Electricity/Water, Salaries, Maintenance, Packaging Bags, Transport, Spoilage.
   - Payment method (Cash from register, Bank Transfer, UPI).

3. Expense Records Table:
   - Expense ID
   - Date
   - Expense Title / Description
   - Category (Color-coded badge)
   - Amount (₹)
   - Paid Via (Register Cash / Bank)
   - Receipt / Voucher Reference #
   - Recorded By staff
   - Actions: View Voucher, Edit, Delete.

4. "RECORD EXPENSE MODAL" (#expense-modal):
   - Expense Title (e.g. "Electricity Bill - September")
   - Category dropdown
   - Amount in INR
   - Payment Method
   - Expense Date
   - Deduct from Cash Register? (Yes/No toggle)
   - Receipt Voucher upload / image URL
   - Notes / Vendor details
   - Save Expense button.
```

---

## Page 11: Reports & Business Intelligence (`reports.html`)

### Stitch Prompt:
```text
Create an executive Reports, Business Intelligence & Financial Analytics page for "reports.html".

Design Theme & CSS:
- Link css/style.css, css/layout.css, css/tables.css, css/forms.css, css/responsive.css.
- Page title: "Reports & Analytics | FreshGrocery".
- Active page ID: 'reports'.

Layout & Components:
1. Date Range & Report Filter Bar:
   - Quick date buttons: Today, Yesterday, This Week, This Month, This Fiscal Year.
   - Custom Date From - To inputs.
   - "Export All Reports (CSV / Excel)" dropdown.

2. Executive Financial Summary Cards (P&L at a glance):
   - Gross Revenue: ₹5,80,400 (Green up trend +12%)
   - Cost of Goods Sold (COGS): ₹4,12,000
   - Gross Profit: ₹1,68,400 (Margin: 29.0%)
   - Operating Expenses: ₹64,200
   - Net Profit: ₹1,04,200 (Margin: 17.9%)

3. Visual Analytics Section (Two Column Grid):
   - Left Card: Sales Trend & Revenue Breakdown (Styled chart placeholder or clean SVG bar/line chart depicting daily revenue).
   - Right Card: Category Revenue Share (Styled breakdown bars showing Dairy 32%, Staples 28%, Produce 18%, Beverages 12%, Others 10%).

4. Top Selling Products Leaderboard:
   - Rank #, Product Name, Category, Units Sold, Total Revenue Generated, Profit Margin %.

5. Quick Report Downloader Hub:
   - Grid of report cards with instant CSV export buttons:
     - 📊 Sales Ledger Report (By date, item, cashier)
     - 📦 Inventory Valuation & Stock Aging Report
     - ⚠️ Expiry & Wastage Audit Report
     - 💰 Profit & Loss Statement (P&L)
     - 🧾 Tax & GST Summary (CGST/SGST collected vs paid)
```

---

## Page 12: Alerts & Notification Center (`notifications.html`)

### Stitch Prompt:
```text
Create a real-time Alerts, Expiry Warnings & Operational Notifications page for "notifications.html".

Design Theme & CSS:
- Link css/style.css, css/layout.css, css/tables.css, css/forms.css, css/responsive.css.
- Page title: "Alerts & Notifications | FreshGrocery".
- Active page ID: 'notifications'.

Layout & Components:
1. Header & Actions:
   - Title: "Alerts & Notifications Center"
   - Subtitle: "Live alerts for stock shortages, imminent expirations, and purchase arrivals".
   - Actions: "Mark All as Read ✅", "Clear Resolved 🧹".

2. Notification Category Tabs:
   - All Alerts (18)
   - 🔴 Critical Low Stock (6)
   - 🟡 Expiring Soon Batches (8)
   - 🔵 Purchase & Inward Updates (4)

3. Interactive Notification Cards Feed:
   - Low Stock Alert Card:
     - Red warning icon, Title "Critical Low Stock: Amul Taaza Milk 500ml", "Current stock: 4 units (Threshold: 20 units)".
     - Action buttons: "Create Purchase Order Now", "Dismiss".
   - Expiry Alert Card:
     - Amber timer icon, Title "Batch BATCH-2024-88 Expiring in 4 Days: Britannia Brown Bread", "Remaining stock: 18 loaves. Expiry Date: Oct 16, 2024".
     - Action buttons: "Apply 30% Clearance Discount", "Write Off / Remove".
   - Purchase Arrival Card:
     - Blue truck icon, Title "PO-2024-104 Out for Delivery", "Supplier Metro Cash & Carry scheduled for delivery today at 4:00 PM".
     - Action button: "Open Inward Screen".

4. Alert Notification Preferences Widget:
   - Quick toggles: Enable low stock email alerts, set expiry warning threshold (7, 15, 30 days).
```

---

## Page 13: User & Staff Access Control (`users.html`)

### Stitch Prompt:
```text
Create a Role-Based User Management & Staff Directory page for "users.html".

Design Theme & CSS:
- Link css/style.css, css/layout.css, css/tables.css, css/forms.css, css/responsive.css.
- Page title: "User Management | FreshGrocery".
- Active page ID: 'users'.

Layout & Components:
1. Staff Roles Overview Ribbon:
   - Admin (Full System Control): 2
   - Store Manager (Inventory & Purchases): 2
   - Cashiers (POS & Billing Terminal): 5
   - Inventory Staff (Stock & Inwarding): 3
   - "➕ Add New Staff Member" button (#btn-add-user).

2. Staff Roster Table:
   - Staff Avatar & Full Name
   - Username
   - Assigned Role (Badge: ADMIN [Rose], STORE_MANAGER [Amber], CASHIER [Green], INVENTORY_STAFF [Blue])
   - Email & Phone
   - Account Status (Active toggle / Inactive badge)
   - Last Login Timestamp
   - Actions: Edit Role, Reset Password, Deactivate Account.

3. "ADD / EDIT STAFF USER" MODAL (#user-modal):
   - Full Name (#user-name)
   - Username (#user-username)
   - Email Address (#user-email)
   - Phone Number (#user-phone)
   - Role Selector (#user-role) [Dropdown: ADMIN, STORE_MANAGER, CASHIER, INVENTORY_STAFF]
   - Initial Password (#user-password)
   - Status (Active / Inactive)
   - Save User button.

4. "RESET PASSWORD MODAL" (#reset-password-modal):
   - Staff Name display, New Temporary Password input, Confirm button.
```

---

## Page 14: Store Settings & POS Configuration (`settings.html`)

### Stitch Prompt:
```text
Create a comprehensive Store Settings & POS Configuration page for "settings.html".

Design Theme & CSS:
- Link css/style.css, css/layout.css, css/tables.css, css/forms.css, css/responsive.css.
- Page title: "Store Settings | FreshGrocery".
- Active page ID: 'settings'.

Layout & Navigation:
- Vertical or horizontal tabs navigation:
  - Tab 1: "Store Profile" (#tab-store)
  - Tab 2: "Tax & GST Configuration" (#tab-tax)
  - Tab 3: "Receipt & Thermal Printer" (#tab-receipt)
  - Tab 4: "Inventory & POS Rules" (#tab-rules)
  - Tab 5: "Backup & System Info" (#tab-system)

Tab Contents:
1. Store Profile:
   - Store Name, Store Legal Entity, Tagline, Phone, Email, Physical Address, City, State, PIN Code, Store Logo upload preview.
2. Tax & GST Configuration:
   - GSTIN / Tax Number, Default Tax Rates (0%, 5%, 12%, 18%), Tax inclusive/exclusive pricing toggle.
3. Receipt & Thermal Printer:
   - Receipt Header Text, Receipt Footer Message (e.g. "Thank you for shopping at FreshGrocery! Items once sold can be returned within 3 days."), Show Barcode on receipt toggle, Printer Paper Width (58mm / 80mm).
4. Inventory & POS Rules:
   - Global Default Low-Stock Reorder Threshold (e.g. 10 units), Near-Expiry Warning Window (e.g. 30 days), Allow Negative Inventory at POS toggle (Yes/No), Enable Customer Loyalty Points toggle (1 point per ₹100 spent).
5. System Info & Save:
   - Database Connection Status (MongoDB: Connected), App Version v1.0.0, "Save All Settings" button.
```

---

## Workflow Summary: How to Proceed
1. **Copy Prompt**: Copy the relevant prompt from above.
2. **Generate with Stitch**: Paste into Stitch.
3. **Save into `frontend/`**: Save the generated HTML file with the specified filename (e.g. `frontend/pos.html`, `frontend/products.html`, etc.).
4. **Notify in Chat**: When you have added any or all pages, let me know!
5. **Backend Wiring**: We will immediately connect the forms, tables, and buttons to the Spring Boot REST endpoints (`/api/v1/...`).
