# TEST_PLAN.md

## Testing Philosophy

Do not consider a feature complete because the page loads.

A feature is complete only when:

1. Expected behavior works.
2. Invalid input is handled.
3. Database state is correct.
4. Important edge cases are tested.

## Authentication

### TC-AUTH-01
Valid credentials should log in successfully.

### TC-AUTH-02
Invalid password should fail without revealing sensitive information.

### TC-AUTH-03
Unauthenticated user should not access protected pages.

## Products

### TC-PROD-01
Create valid product.

### TC-PROD-02
Reject missing product name.

### TC-PROD-03
Reject invalid price.

### TC-PROD-04
Search product.

### TC-PROD-05
Edit product.

### TC-PROD-06
Deactivate product.

## Inventory

### TC-INV-01
Increase stock.

### TC-INV-02
Reject negative stock update.

### TC-INV-03
Display low-stock product.

### TC-INV-04
Prevent stock from becoming negative.

## POS

### TC-POS-01
Add product to cart.

### TC-POS-02
Reject quantity greater than available stock.

### TC-POS-03
Calculate subtotal correctly.

### TC-POS-04
Calculate total correctly.

### TC-POS-05
Complete sale.

### TC-POS-06
Verify stock decreases after sale.

### TC-POS-07
Verify sale and sale items are saved.

### TC-POS-08
Verify failed sale does not partially modify inventory.

## Customers

### TC-CUST-01
Create customer.

### TC-CUST-02
Search customer.

### TC-CUST-03
View purchase history.

## Reports

### TC-REP-01
Daily sales total matches stored sales.

### TC-REP-02
Inventory report matches current stock.

## Security

- Passwords are hashed.
- Secrets are not committed.
- Authorization is enforced server-side.
- SQL injection protections are present.
- User input is validated.
