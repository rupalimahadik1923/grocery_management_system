# Grocery Management System - Setup & Deployment Guide

## 1. Prerequisites
- **Database**: MongoDB v6.0+ (Running on `localhost:27017`)
- **Backend**: Java JDK 17 (or JDK 8 with Spring Boot 2.7.x) & Maven
- **Frontend**: Any modern web browser (Edge, Chrome, Firefox)

---

## 2. Database Verification
To verify your MongoDB database is running:
```powershell
Get-Service -Name "*mongo*"
```
If not started, start the Windows service:
```powershell
Start-Service MongoDB
```

---

## 3. Running the Backend
1. Navigate to the backend directory:
   ```powershell
   cd backend
   ```
2. Run using Maven / Maven Wrapper:
   ```powershell
   mvn spring-boot:run
   ```
   *(Or `./mvnw.cmd spring-boot:run`)*
3. The server will start on `http://localhost:8080`.
4. The database seeder will automatically create the default accounts:
   - **Admin**: `admin` / `admin123`
   - **Store Manager**: `manager` / `manager123`
   - **Cashier**: `cashier` / `cashier123`
   - **Inventory Staff**: `inventory` / `inventory123`

---

## 4. Running the Frontend
1. The frontend consists of pure, standard HTML5, CSS3, and JavaScript modules.
2. Open `frontend/login.html` directly in your browser, or serve it using any local static web server.
3. Login using the default credentials above to explore the dashboard.
