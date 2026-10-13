# Grocery Management System - Software Requirements Specification

**Version:** 1.0  
**Application Type:** Full-stack web application  
**Target Environment:** Antigravity AI Coding Environment  
**Tech Stack:** HTML5, Vanilla CSS3, Vanilla JavaScript (ES6), Java Spring Boot, Spring Data MongoDB, MongoDB v6+

---

## 1. Project Objectives
- Digitize complete grocery store operations.
- Eliminate manual stock counting and checkout billing errors.
- Real-time batch-level inventory tracking with FEFO (First-Expired, First-Out) and FIFO rules.
- Automated alerts for low stock and near-expiry goods.
- Comprehensive Supplier Purchasing lifecycle (PO -> Goods Receiving -> Stock Inward).
- High-speed Point of Sale (POS) with barcode search and printable receipts.
- Auditable returns and restocking workflows.
- Operating expense tracking and real-time Profit & Loss reporting.
- Strict Role-Based Access Control (Admin, Store Manager, Cashier, Inventory Staff).

---

## 2. User Roles & Permissions
1. **ADMIN**: Unrestricted access to all modules, store settings, user management, financial reports, audit logs.
2. **STORE_MANAGER**: Dashboard, product management, purchase orders, sales review, inventory adjustments, reports.
3. **CASHIER**: Point of Sale (POS) checkout, product price inquiry, receipt reprints, customer lookup.
4. **INVENTORY_STAFF**: Stock balance tracking, goods receiving from suppliers, batch expiry monitoring, stock counting and adjustment submissions.

---

## 3. Technology Stack & Rules
- **Frontend**: Plain HTML5, CSS3, and Vanilla JavaScript (Fetch API). No external frontend frameworks (No React, Vue, Angular, Next.js).
- **Backend**: Java Spring Boot REST API, layered architecture (Controller, Service, Repository, DTO, Security).
- **Persistence**: MongoDB document database via Spring Data MongoDB.
- **Security**: Spring Security with BCrypt password hashing and JWT / Token authentication.
- **Data Integrity**: Concurrency control for stock deduction during checkout, decimal-safe financial calculations, immutable transaction audit ledger.
