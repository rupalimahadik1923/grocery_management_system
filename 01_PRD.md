# Product Requirements Document — Smart Grocery Store Management System

## 1. Project Identity

**Project:** Smart Grocery Store Management System  
**Type:** College Field Project  
**Academic context:** S.Y. B.Sc. Computer Science  
**Primary goal:** Convert the approved project concept into a working web application.

The approved material describes the system as a digital solution for grocery-store operations, including inventory, sales, transactions, and customer service from one integrated platform.

## 2. Approved Core Idea

The system should reduce dependence on manual grocery-store processes by providing computerized workflows for:

- Inventory management
- Billing / Point of Sale
- Sales tracking
- Customer management
- Stock alerts
- Reporting and analytics

The source project specifically identifies automation, error reduction, speed improvement, and better customer service as objectives.

## 3. Problem Statement

Traditional grocery stores can face:

- Time-consuming and inaccurate manual inventory counting
- Slow manual billing and calculation
- Poor customer purchase-history/loyalty management
- Lack of real-time sales and inventory information

## 4. MVP Goal

Build a reliable local web application that demonstrates the complete basic store workflow:

1. Staff/admin logs in.
2. Products can be added, edited, viewed, searched, and deactivated.
3. Stock quantities can be updated.
4. Low-stock products can be identified.
5. A sale can be created.
6. Billing totals are calculated automatically.
7. Stock decreases after a successful sale.
8. Customer information can be stored.
9. Purchase history can be viewed.
10. Basic sales and inventory reports can be generated.
11. The application provides clear success/error messages.

## 5. Users

### Admin / Store Manager

Responsibilities:

- Manage products
- Manage categories
- Manage stock
- View sales
- View reports
- Manage customers
- Monitor low-stock items

### Staff / Cashier

Responsibilities:

- Search products
- Create bills
- Add products to a cart
- Complete a sale
- View customer information when required

### Customer

For the MVP, customer functionality can be represented through customer records and purchase history.

Do not build a separate customer mobile application unless specifically requested later.

## 6. Functional Requirements

### FR-01 Authentication

The application should provide login for authorized store users.

Minimum MVP:

- Username/email
- Password
- Role
- Logout

Passwords must never be stored as plain text.

### FR-02 Product Management

Product fields:

- Product ID
- Product name
- Category
- Price
- Stock quantity
- Minimum/reorder level
- Status

Actions:

- Add
- View
- Search
- Edit
- Deactivate

### FR-03 Inventory Management

The system should:

- Display current stock
- Allow stock updates
- Identify low-stock products
- Prevent negative stock
- Record important stock changes

### FR-04 POS / Billing

The billing workflow should:

1. Select/search product
2. Enter quantity
3. Validate stock
4. Add to cart
5. Calculate item subtotal
6. Calculate bill total
7. Confirm sale
8. Save sale
9. Decrease stock
10. Display receipt/bill summary

### FR-05 Sales Tracking

Reports should support at least:

- Today's sales
- Number of transactions
- Total sales amount
- Best-selling products where practical
- Sales history

### FR-06 Customer Management

Store:

- Customer ID
- Name
- Phone/email if required
- Purchase history

Do not collect unnecessary personal data.

### FR-07 Dashboard

The dashboard should show useful summary information such as:

- Total products
- Low-stock products
- Today's sales
- Number of customers
- Recent transactions

### FR-08 Search and Filtering

Provide practical search/filter functionality for:

- Products
- Categories
- Customers
- Sales

## 7. Non-Functional Requirements

### Usability

The UI should be simple enough for a beginner to demonstrate.

### Reliability

Important operations should validate input and handle errors.

### Security

- Password hashing
- Server-side authorization
- Input validation
- Parameterized database queries
- No secrets committed to Git

### Maintainability

Use clear folder structure, meaningful names, reusable functions, and documentation.

### Performance

For the college MVP, local response times should be fast for a small dataset.

## 8. Recommended Technology Stack

### Frontend

- HTML5
- CSS3
- Vanilla JavaScript

### Backend

- Python
- Flask

### Database

- MySQL

### API

- REST-style JSON endpoints where appropriate

### Development

- Google Antigravity
- Git
- GitHub

### Why this stack?

It stays close to the technology listed in the approved material while reducing complexity for a beginner. React, Spring Boot, microservices, Docker, cloud infrastructure, and ML are intentionally excluded from the MVP.

## 9. Database — Initial Entities

Recommended entities:

- users
- categories
- products
- inventory_transactions
- customers
- sales
- sale_items

Optional later:

- loyalty_accounts
- loyalty_transactions
- suppliers
- purchase_orders
- audit_logs

## 10. Important Business Rules

1. Product price must be zero or greater.
2. Stock cannot become negative.
3. Sale quantity must be positive.
4. A sale cannot be completed if stock is insufficient.
5. Completing a sale must update stock.
6. Sale total must be calculated by the server.
7. Client-side totals are for display only.
8. Deleted/deactivated products should not appear as normal purchasable products.
9. Database transactions should be used for sale + stock update.
10. Unauthorized users must not access admin-only operations.

## 11. MVP Boundaries

### Build now

- Login
- Dashboard
- Product management
- Inventory
- Customer management
- POS/cart
- Sales
- Basic reports
- Validation
- Error handling
- Testing
- Documentation

### Keep as future enhancement

The approved project mentions:

- AI-based product suggestions
- Mobile self-scanning
- Smart demand forecasting

These should remain future enhancements unless the guide specifically asks for them.

## 12. Success Criteria

The MVP is successful when a complete demonstration can be performed:

Login → Dashboard → Add product → Add stock → Create customer → Create bill → Complete sale → Stock decreases → Sales report updates.

The system must also demonstrate validation and error handling.

## 13. Open Questions

Before productionizing beyond the MVP, confirm:

1. Should there be one role or separate Admin/Cashier roles?
2. Is customer login required, or is customer management enough?
3. Is online payment required? Recommended answer for MVP: no.
4. Is barcode hardware actually required? Recommended answer for MVP: no; product search can simulate it.
5. Is multi-location support required for the current submission? Recommended answer: future scope.
6. Does the guide require Java instead of Python? If yes, use Spring Boot instead of Flask.
