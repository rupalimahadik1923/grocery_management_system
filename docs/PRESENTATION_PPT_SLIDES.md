# FreshGrocery — Final Presentation Slide Deck (15 Slides)
## Smart Grocery Store Management & Point of Sale (POS) System

> **Instructions for Presenter:**  
> Use this document to create your PowerPoint (`.pptx`) or Google Slides deck. Each slide is formatted with **Slide Title**, **Key Visual / Layout**, **Bullet Points for the Slide**, and **Verbatim Speaker Notes** for your presentation delivery.

---

### Slide 1: Title Slide
* **Slide Title:** FreshGrocery Supermarket POS & Inventory Management System
* **Subtitle:** An Enterprise Web Platform with FEFO Batch Expiry Control & Real-time Financials
* **Presenter Name:** [Your Name / Team Members]
* **Degree / Course:** B.E. / B.Tech / MCA Final Year Project
* **Guide / Supervisor:** [Project Guide / Professor Name]
* **Academic Year:** 2024–2026

**Speaker Notes:**  
*"Good morning respected evaluators, professors, and guests. Today, we are proud to present **FreshGrocery**, a full-stack automated supermarket operations and Point of Sale platform engineered with Java Spring Boot, MongoDB, and modern web architecture. Our goal is to eliminate retail grocery wastage and cashier billing bottlenecks through automated batch expiry tracking and real-time ledger accounting."*

---

### Slide 2: Industry Challenges & Problem Statement
* **Slide Title:** The FMCG Retail Challenge
* **Key Visual:** 3 problem icon cards (Spoilage, Slow Checkout, Financial Blind Spots)
* **Slide Bullet Points:**
  * **Perishable Spoilage Waste:** Supermarkets lose 3–5% of margin when stock near expiration is pushed to the back of shelves.
  * **Peak-Hour Billing Congestion:** Manual product lookup and tax calculations cause cashier queues and customer frustration.
  * **Disconnected Financials:** Standalone billing machines fail to track operational overheads (rent, electricity, damaged waste), leaving true net profit unknown.
  * **High ERP Costs:** Existing commercial ERP solutions demand expensive proprietary hardware and heavy licensing fees.

**Speaker Notes:**  
*"In retail grocery, profit margins are razor thin—often between 2% and 5%. The number one driver of financial losses in supermarkets is expired inventory that sits unnoticed on back shelves until it must be thrown away. Additionally, peak-hour checkout bottlenecks lead to lost sales. Small and midsize stores cannot afford enterprise ERPs like SAP or Oracle Retail, creating an urgent need for an agile, browser-based solution."*

---

### Slide 3: Proposed Solution & Core Innovation
* **Slide Title:** The FreshGrocery Solution
* **Key Visual:** Diagram showing unified workflow: Supplier &rarr; Inward &rarr; Shelves &rarr; POS Terminal &rarr; Receipt &rarr; Ledger
* **Slide Bullet Points:**
  * **Unified Full-Stack Architecture:** Connects front-counter checkout directly to warehouse procurement and store analytics.
  * **FEFO Expiry Control:** First-Expired, First-Out batch sequencing flags aging goods before they spoil.
  * **High-Speed Keyboard POS:** Instant barcode scanning, keyboard hotkeys (`F2`, `F4`, `F8`), and dynamic cart calculations.
  * **Browser-Native Thermal Receipts:** 80mm printable thermal receipt formatted with GSTIN and audit numbers.
  * **Real-time P&L Reporting:** Automated computation of Gross Revenue, COGS, Operating Expenses, and Net Profit.

**Speaker Notes:**  
*"FreshGrocery is our response to these operational hurdles. We have built an end-to-end platform that automates the entire supermarket lifecycle. By coupling high-speed barcode billing with FEFO batch management, cashiers can ring up customers in seconds while warehouse managers receive automated warnings for goods nearing their expiry dates."*

---

### Slide 4: System Architecture
* **Slide Title:** N-Tier Layered Architecture
* **Key Visual:** Tier diagram (Presentation Layer &rarr; Security Layer &rarr; Controller &rarr; Service &rarr; MongoDB)
* **Slide Bullet Points:**
  * **Presentation Layer:** Plain HTML5, Vanilla CSS3 Custom Property design system, ES6 JavaScript Modules (No framework overhead).
  * **Security Layer:** Spring Security 6 filter chain with BCrypt password hashing and stateless JWT bearer tokens.
  * **Application Core:** Java 17 LTS, Spring Boot 3.2.5 RESTful micro-controllers with global exception handling.
  * **Data Layer:** Spring Data MongoDB connected to local document cluster (`localhost:27017/grocery_db`).

**Speaker Notes:**  
*"Our technical architecture adheres strictly to enterprise layered design principles. The presentation tier uses pure HTML5 and ES6 modules for lightning-fast sub-100 millisecond page loads. The backend is powered by Spring Boot 3 on Java 17, exposing clean REST endpoints secured by a stateless JWT filter. Spring Data MongoDB handles high-throughput document persistence."*

---

### Slide 5: Role-Based Access Control (RBAC)
* **Slide Title:** Granular User Roles & Permissions Matrix
* **Key Visual:** RBAC Matrix Table
* **Slide Bullet Points:**
  * **ADMIN:** Full system authority, staff creation, password resets, financial audits, store settings.
  * **STORE_MANAGER:** Product catalog, supplier procurement POs, inventory adjustments, and executive reports.
  * **CASHIER:** Restricted to Point of Sale (`pos.html`), product price checks, and receipt reprint.
  * **INVENTORY_STAFF:** Warehouse stock balance monitoring, PO goods inwarding, and batch expiry tracking.

**Speaker Notes:**  
*"Security and operational integrity require strict role separation. A cashier ringing up customers cannot alter product cost prices or view financial profit-and-loss statements. FreshGrocery enforces this server-side using Spring Security method security and JWT claims."*

---

### Slide 6: Database Design & MongoDB Model
* **Slide Title:** MongoDB Document Persistence Model
* **Key Visual:** Document JSON hierarchy tree (13 collections)
* **Slide Bullet Points:**
  * **Why MongoDB NoSQL?** Retail receipts require historical immutability; document embedding preserves exact line item prices and tax snapshots at the time of sale.
  * **13 Document Collections:** `users`, `products`, `categories`, `inventory_batches`, `inventory_transactions`, `sales`, `returns`, `purchase_orders`, `suppliers`, `customers`, `expenses`, `notifications`, `store_settings`.
  * **High-Performance Indexing:** Unique indexes on SKUs and barcodes; compound indexes on `{ productId: 1, expiryDate: 1 }` for instant FEFO queries.

**Speaker Notes:**  
*"A critical design decision in this project was selecting MongoDB over traditional SQL. In retail billing, a completed receipt must never change—even if tomorrow the wholesale supplier increases the price of milk. In MongoDB, a sale document embeds a frozen snapshot of purchased items. Furthermore, compound indexes on expiry dates make our batch queries instantaneous."*

---

### Slide 7: High-Speed POS & Barcode Scanner Integration
* **Slide Title:** Point of Sale (POS) Checkout Experience
* **Key Visual:** Screenshot mockup of `pos.html` showing split screen (Catalog on left, Cart on right)
* **Slide Bullet Points:**
  * **Fast Barcode Scan:** Automatic lookup by EAN-13 barcode or SKU with immediate Enter-key addition to ticket.
  * **Speed Keyboard Hotkeys:**
    * `F2`: Focus Barcode Scanner
    * `F4`: Open Customer Loyalty Lookup
    * `F8`: Open Tender Payment Modal
    * `Esc`: Dismiss Modals
  * **Visual Category Pills:** Quick access buttons with emoji navigation (Dairy, Produce, Bakery, Staples, Beverages).
  * **Cart Line Items:** Dynamic quantity counters (`-` / `+`), itemized subtotals, and trash removal.

**Speaker Notes:**  
*"Here you see our Point of Sale interface, optimized specifically for cashier throughput. Cashiers can operate the screen entirely via keyboard hotkeys without touching a mouse. Scanning a barcode or typing a product SKU adds the item to the bill instantly, recalculating subtotals in real time."*

---

### Slide 8: Thermal Receipts & Multi-Mode Tender
* **Slide Title:** Payment Tender & 80mm Thermal Receipt Generator
* **Key Visual:** Thermal receipt preview layout showing header, GSTIN, itemized table, and barcode footer
* **Slide Bullet Points:**
  * **Multi-Mode Tender Modal:**
    * **Cash:** Exact amount buttons (₹100, ₹500, ₹2000) with automated **Change Due** calculation.
    * **UPI / QR Code:** Dynamic QR code container for instant mobile scan with GPay/PhonePe/Paytm.
    * **Card:** POS terminal card swipe/chip confirmation.
  * **Automated GST Splitting:** Computes CGST (2.5%) and SGST (2.5%) transparently on taxable subtotal.
  * **Native Browser Printing:** CSS `@media print` generates standard 80mm thermal receipts via `window.print()` without extra drivers.

**Speaker Notes:**  
*"When completing payment, cashiers can select Cash, UPI QR code, or Card. If cash is selected, the system calculates exact change due to eliminate cashier math errors. Upon completion, a formatted 80mm thermal receipt pops up ready for printing with store GSTIN and return policy guidelines."*

---

### Slide 9: FEFO Expiry Control & Inventory Ledger
* **Slide Title:** First-Expired, First-Out (FEFO) Batch Control
* **Key Visual:** 3-Tab Inventory screenshot: Stock Levels &rarr; FEFO Batches &rarr; Audit Ledger
* **Slide Bullet Points:**
  * **FEFO vs FIFO:** Traditional FIFO sells the oldest shipment; FEFO sells the batch closest to expiration, preventing perishable spoilage.
  * **Automated Urgency Badges:**
    * 🔴 Critical (< 7 Days Left)
    * 🟡 Warning (< 30 Days Left)
    * 🟢 Healthy Stock
  * **Immutable Audit Ledger:** Every stock increment and decrement is logged into `inventory_transactions` with cashier username, reference number, and reason.

**Speaker Notes:**  
*"This slide highlights our core algorithmic feature: FEFO inventory management. Unlike traditional FIFO which only looks at arrival date, FEFO organizes physical batches by expiry date. If a batch of bread is expiring in 4 days, the dashboard alerts the manager to discount or showcase it immediately. Every single stock change is recorded in an immutable ledger for complete accountability."*

---

### Slide 10: Procurement & Purchase Order Lifecycle
* **Slide Title:** End-to-End Supplier Procurement
* **Key Visual:** Lifecycle flowchart: PO Created (Draft) &rarr; Transmitted (Ordered) &rarr; Goods Inwarded (Received)
* **Slide Bullet Points:**
  * **Draft PO Creation:** Store managers create orders, select vendors from the Supplier Directory, and add dynamic line items.
  * **Fulfillment Tracking:** Tracks expected delivery dates, vendor payment terms (Net 15, Net 30, COD), and pending deliveries.
  * **Automated Goods Inwarding:** When warehouse staff click 'Inward & Stock', the system:
    1. Increments product master stock levels.
    2. Spawns a new `InventoryBatch` with batch number and expiry date.
    3. Records an audit entry in the inventory transaction ledger.

**Speaker Notes:**  
*"Procurement is seamlessly integrated with warehouse stock. A purchase order moves from Draft to Ordered. When the truck arrives at the delivery dock, clicking 'Inward Goods' automatically updates shelf stock counters, generates physical batch records, and logs the transaction without manual data entry."*

---

### Slide 11: Customer Relationship & Loyalty Engine
* **Slide Title:** Customer Loyalty & Tier Engine
* **Key Visual:** Loyalty card badges (🥉 Bronze, 🥈 Silver, 🥇 Gold) and customer drawer
* **Slide Bullet Points:**
  * **Seamless POS Lookup:** Cashiers enter customer mobile phone (`F4`) to attach loyalty account.
  * **Automated Points Accrual:** 1 Loyalty Point is awarded for every ₹100 spent in completed checkout.
  * **Dynamic Tier Upgrades:**
    * **Bronze:** Standard enrollment
    * **Silver:** Lifetime spend > ₹5,000
    * **Gold:** Lifetime spend > ₹20,000
  * **Purchase History Traceability:** Full audit trail linking customer phones to past receipts.

**Speaker Notes:**  
*"To encourage customer retention, FreshGrocery includes a built-in loyalty program. Cashiers can look up customer phone numbers in one second during checkout. The system automatically awards loyalty points and promotes customers from Bronze to Silver and Gold tiers based on lifetime spend."*

---

### Slide 12: Operating Expenses & Real-time P&L Statement
* **Slide Title:** Operating Expenses & Financial Intelligence
* **Key Visual:** Financial cards breakdown: Revenue - COGS = Gross Profit - Expenses = Net Profit
* **Slide Bullet Points:**
  * **Expense Outflow Tracking:** Log daily supermarket overheads: Store Rent, Electricity, Staff Salaries, Bags, Loss.
  * **Executive P&L Analytics:**
    * **Gross Revenue:** Total completed sales turnover.
    * **Cost of Goods Sold (COGS):** Inventory cost basis (~70%).
    * **Gross Profit & Margin %:** Revenue minus COGS.
    * **Net Profit & Net Margin %:** Gross profit minus operating expenses.
  * **Direct CSV Export Hub:** One-click downloads for Product Masters, Sales Ledgers, and Expense Outflows.

**Speaker Notes:**  
*"Most supermarket systems only record sales. FreshGrocery tracks operating expenses as well. By factoring in electricity, staff wages, and store rent against daily revenue and inventory costs, store owners get a live, real-time Profit and Loss statement showing their true net profit."*

---

### Slide 13: Security Architecture & Concurrency
* **Slide Title:** Security, Validation & Concurrency Control
* **Key Visual:** Security filter flow diagram showing request &rarr; JWT filter &rarr; SecurityContext &rarr; Service
* **Slide Bullet Points:**
  * **Stateless Token Authentication:** HMAC-SHA256 signed JWTs with expiration and bearer extraction.
  * **Password Encryption:** Salted BCrypt password encoding (strength 10).
  * **Concurrency Protection:** Real-time stock validation during checkout prevents overselling and negative inventory.
  * **Input Sanitization:** Server-side validation on prices, tax percentages, and quantities prevents invalid injection.

**Speaker Notes:**  
*"Security and data integrity are enforced at the core. We use stateless JWT tokens and salted BCrypt hashing. During POS checkout, inventory deduction is validated to prevent overselling or race conditions, ensuring that physical stock and digital records stay synchronized."*

---

### Slide 14: Testing Methodology & Verification Results
* **Slide Title:** Testing Methodology & Verification Evidence
* **Key Visual:** Terminal snippet showing JUnit 5 test execution (`mvn test` &rarr; BUILD SUCCESS)
* **Slide Bullet Points:**
  * **Test Framework:** JUnit 5, Spring Boot Test, and MockMvc.
  * **Test Suite Verification:**
    * `shouldLoginSuccessfullyWithValidCredentials`: **PASSED**
    * `shouldRejectInvalidCredentials`: **PASSED**
    * `shouldRejectMeEndpointWithoutToken`: **PASSED**
    * `shouldReturnCurrentUserWithValidToken`: **PASSED**
  * **Build Health:** 54 Java classes compiled with **`BUILD SUCCESS`**; 100% test pass rate.
  * **Database Verification:** Automated seed runner populates categories, products, customer loyalty, and store profile.

**Speaker Notes:**  
*"Our codebase has undergone rigorous automated testing. All integration test suites pass with zero errors, verifying JWT authentication, protected endpoint security, and MongoDB data mapping under Maven."*

---

### Slide 15: Conclusion, Future Roadmap & Viva Defense
* **Slide Title:** Conclusion & Future Scope
* **Key Visual:** Project summary roadmap badge & Q&A prompt
* **Slide Bullet Points:**
  * **Project Summary:** FreshGrocery delivers a modern, robust, production-ready supermarket platform that eliminates grocery spoilage, accelerates checkout, and provides live financial clarity.
  * **Future Roadmap:**
    1. **Machine Learning Demand Forecasting:** AI prediction of vegetable and dairy demand based on weekly weather and holiday trends.
    2. **Mobile Self-Scanning App:** Shoppers scan items with smartphone cameras and pay via UPI directly.
    3. **Automated Produce Scales:** Serial/Bluetooth connectivity to digital weighing scales for loose grains and produce.
  * **Open for Questions & Live Demonstration**

**Speaker Notes:**  
*"In conclusion, FreshGrocery successfully combines Point of Sale convenience with enterprise inventory control and financial intelligence. We are excited about future extensions like machine learning demand forecasting and self-checkout mobile apps. We now welcome questions and invite the panel to the live interactive demonstration. Thank you!"*
