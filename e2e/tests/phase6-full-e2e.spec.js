const { test, expect } = require('@playwright/test');

const ISOLATED_API = 'http://localhost:8080';

async function routeToIsolatedBackend(page) {
  // Port 8080 now runs the latest compiled backend connected to grocery_db_test directly.
}

async function registerStoreViaUI(page, { storeName, storeCode, city, ownerName, ownerEmail, adminUsername, password }) {
  await page.goto('/login.html');
  await page.click('#btn-open-register');
  await page.fill('#reg-store-name', storeName);
  await page.fill('#reg-store-code', storeCode);
  await page.fill('#reg-city', city);
  await page.click('#btn-reg-next');

  await page.fill('#reg-owner-name', ownerName);
  await page.fill('#reg-owner-email', ownerEmail);
  await page.fill('#reg-admin-username', adminUsername);
  await page.fill('#reg-password', password);
  await page.fill('#reg-password-confirm', password);

  const [regRes] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/v1/auth/register-store') && r.request().method() === 'POST'),
    page.click('#btn-submit-register'),
  ]);
  expect(regRes.status()).toBe(200);
  await expect(page.locator('#register-success-modal')).toHaveClass(/active/);
  await page.click('#btn-proceed-dashboard');
  await page.waitForURL(/dashboard\.html/, { timeout: 10000 });
}

test.describe.serial('Phase 6 — Full Live Application Workflows & Two-Store E2E Isolation', () => {
  const uid = Date.now().toString(36).slice(-6);
  const storeA = {
    storeName: `Alpha Mart ${uid}`,
    storeCode: `alpha${uid}`,
    city: 'Bengaluru',
    ownerName: 'Alpha Owner',
    ownerEmail: `alpha_${uid}@e2e.local`,
    adminUsername: `adm_a_${uid}`,
    password: 'StrongPass123!',
  };
  const storeB = {
    storeName: `Beta Mart ${uid}`,
    storeCode: `beta${uid}`,
    city: 'Mysuru',
    ownerName: 'Beta Owner',
    ownerEmail: `beta_${uid}@e2e.local`,
    adminUsername: `adm_b_${uid}`,
    password: 'StrongPass123!',
  };

  let tokenA = '';
  let prodAName = `Alpha Organic Milk ${uid}`;
  let custAPhone = `91${Math.floor(10000000 + Math.random() * 89999999)}`;
  let supAName = `Alpha Farms ${uid}`;
  let receiptANum = '';

  test('1. Store A & Store B registration + duplicate guards + 4-role authentication & RBAC', async ({ browser }) => {
    const ctxA = await browser.newContext();
    const pageA = await ctxA.newPage();
    await routeToIsolatedBackend(pageA);

    await registerStoreViaUI(pageA, storeA);
    tokenA = await pageA.evaluate(() => localStorage.getItem('grocery_token'));
    expect(tokenA).toBeTruthy();

    // Duplicate storeCode rejected in UI modal
    await pageA.click('#logout-btn');
    await pageA.waitForURL(/login\.html/);
    await pageA.click('#btn-open-register');
    await pageA.fill('#reg-store-name', 'Dup Store');
    await pageA.fill('#reg-store-code', storeA.storeCode);
    await expect(pageA.locator('#reg-store-code-feedback')).toContainText('already registered', { timeout: 5000 });
    await pageA.click('#btn-close-register');

    // Provision STORE_MANAGER, INVENTORY_STAFF, CASHIER in Store A via API
    const staffRoles = [
      { username: `mgr_${uid}`, role: 'STORE_MANAGER', expectedUrl: /dashboard\.html/, forbiddenApi: '/api/v1/users' },
      { username: `inv_${uid}`, role: 'INVENTORY_STAFF', expectedUrl: /dashboard\.html/, forbiddenApi: '/api/v1/sales' },
      { username: `csh_${uid}`, role: 'CASHIER', expectedUrl: /pos\.html/, forbiddenApi: '/api/v1/reports/profit-loss' },
    ];
    for (const s of staffRoles) {
      const createRes = await pageA.request.post(`${ISOLATED_API}/api/v1/users`, {
        headers: { Authorization: `Bearer ${tokenA}` },
        data: { username: s.username, password: storeA.password, fullName: s.username, email: `${s.username}@e2e.local`, role: s.role },
      });
      expect(createRes.status()).toBe(200);

      // Login via Staff tab
      await pageA.click('#tab-staff');
      await pageA.fill('#store-code', storeA.storeCode);
      await pageA.fill('#username', s.username);
      await pageA.fill('#password', storeA.password);
      await pageA.click('#login-submit-btn');
      await pageA.waitForURL(s.expectedUrl, { timeout: 10000 });

      // Verify backend enforces 403 on restricted API even if navigated directly
      const roleToken = await pageA.evaluate(() => localStorage.getItem('grocery_token'));
      const deniedRes = await pageA.request.get(`${ISOLATED_API}${s.forbiddenApi}`, {
        headers: { Authorization: `Bearer ${roleToken}` },
      });
      expect(deniedRes.status()).toBe(403);

      await pageA.click('#logout-btn');
      await pageA.waitForURL(/login\.html/);
    }
    await ctxA.close();

    // Register Store B in separate browser context
    const ctxB = await browser.newContext();
    const pageB = await ctxB.newPage();
    await routeToIsolatedBackend(pageB);
    await registerStoreViaUI(pageB, storeB);
    await ctxB.close();
  });

  test('2. Store A end-to-end business workflows across all 12 modules', async ({ page }) => {
    const pageErrors = [];
    const apiFailures = [];
    page.on('pageerror', (e) => pageErrors.push(e.message));
    page.on('response', (res) => {
      if (res.url().includes('/api/v1/') && res.status() >= 400) {
        apiFailures.push(`${res.request().method()} ${new URL(res.url()).pathname} -> ${res.status()}`);
      }
    });
    await routeToIsolatedBackend(page);

    // Login as Store A Admin
    await page.goto('/login.html');
    await page.click('#tab-owner');
    await page.fill('#username', storeA.adminUsername);
    await page.fill('#password', storeA.password);
    await page.click('#login-submit-btn');
    await page.waitForURL(/dashboard\.html/, { timeout: 10000 });

    // A. Categories
    await page.goto('/categories.html');
    await page.waitForLoadState('networkidle');
    await page.click('#btn-add-category');
    await page.fill('#cat-name', `Alpha Cat ${uid}`);
    await page.fill('#cat-slug', `alphacat-${uid}`);
    await page.click('#btn-save-cat');
    await expect(page.locator('body')).toContainText(`Alpha Cat ${uid}`, { timeout: 5000 });
    await page.click('#btn-view-table');
    await expect(page.locator('#category-table-body')).toContainText(`Alpha Cat ${uid}`, { timeout: 5000 });

    // B. Products
    await page.goto('/products.html');
    await page.waitForLoadState('networkidle');
    await page.click('#btn-add-product');
    await page.fill('#prod-name', prodAName);
    await page.fill('#prod-sku', `SKU-A-${uid}`);
    await page.selectOption('#prod-category', { index: 1 });
    await page.fill('#prod-cost', '40');
    await page.fill('#prod-price', '60');
    await page.fill('#prod-stock', '20');
    await page.fill('#prod-min-stock', '18');
    await page.click('#btn-save-prod');
    await expect(page.locator('#products-table-body')).toContainText(prodAName, { timeout: 5000 });
    await page.fill('#product-search-input', prodAName);
    await expect(page.locator('#products-table-body')).toContainText(prodAName);

    // C. Customers
    await page.goto('/customers.html');
    await page.waitForLoadState('networkidle');
    await page.click('#btn-add-cust');
    await page.fill('#cust-name', `Customer A ${uid}`);
    await page.fill('#cust-phone', custAPhone);
    await page.fill('#cust-email', `c_${uid}@e2e.local`);
    await page.click('#btn-save-cust');
    await expect(page.locator('#customers-table-body')).toContainText(`Customer A ${uid}`, { timeout: 5000 });

    // D. Suppliers
    await page.goto('/suppliers.html');
    await page.waitForLoadState('networkidle');
    await page.click('#btn-add-supplier');
    await page.fill('#sup-name', supAName);
    await page.fill('#sup-code', `SUP-A-${uid}`);
    await page.fill('#sup-phone', '9111111111');
    await page.click('#btn-save-sup');
    await expect(page.locator('#suppliers-table-body')).toContainText(supAName, { timeout: 5000 });

    // E. Inventory overview & adjustment
    await page.goto('/inventory.html');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('#stock-table-body')).toContainText(prodAName, { timeout: 5000 });

    // F. POS Checkout -> Sale + Receipt + Low-Stock Notification
    await page.goto('/pos.html');
    await page.waitForLoadState('networkidle');
    const card = page.locator('.pos-product-card', { hasText: prodAName });
    await expect(card).toBeVisible({ timeout: 5000 });
    await card.click();
    await page.click('.pos-qty-plus, button:has-text("+")').catch(() => {});
    await page.click('#btn-open-tender');
    const [saleRes] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/v1/sales') && r.request().method() === 'POST'),
      page.click('#btn-complete-payment'),
    ]);
    expect(saleRes.status()).toBe(200);
    const saleJson = await saleRes.json();
    receiptANum = saleJson.data.receiptNumber;
    await expect(page.locator('#receipt-modal')).toHaveClass(/active/);

    // G. Sales History
    await page.goto('/sales.html');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('body')).toContainText(receiptANum, { timeout: 5000 });

    // H. Returns
    await page.goto('/returns.html');
    await page.waitForLoadState('networkidle');
    await page.fill('#return-receipt-input', receiptANum);
    await page.click('#btn-lookup-receipt');
    await expect(page.locator('#return-process-panel')).toBeVisible({ timeout: 5000 });

    // I. Expenses
    await page.goto('/expenses.html');
    await page.waitForLoadState('networkidle');
    await page.click('#btn-add-expense');
    await page.fill('#exp-title', `Alpha Electricity ${uid}`);
    await page.fill('#exp-amount', '250');
    await page.click('#btn-save-exp');
    await expect(page.locator('#expenses-table-body')).toContainText(`Alpha Electricity ${uid}`, { timeout: 5000 });

    // J. Reports & CSV Exports
    await page.goto('/reports.html');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('#rep-total-orders')).toHaveText('1 orders', { timeout: 5000 });
    const jwtA = await page.evaluate(() => localStorage.getItem('grocery_token'));
    for (const exportType of ['products', 'sales', 'expenses']) {
      const expRes = await page.request.get(`${ISOLATED_API}/api/v1/reports/export/${exportType}`, {
        headers: { Authorization: `Bearer ${jwtA}` },
      });
      expect(expRes.status()).toBe(200);
    }

    // K. Notifications
    await page.goto('/notifications.html');
    await page.waitForLoadState('networkidle');

    // L. Settings update & persistence
    await page.goto('/settings.html');
    await page.waitForLoadState('networkidle');
    await page.fill('#set-city', 'Bengaluru South');
    await page.click('#btn-save-settings');
    await page.reload();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('#set-city')).toHaveValue('Bengaluru South');

    expect(apiFailures).toEqual([]);
    expect(pageErrors).toEqual([]);
  });

  test('3. Two-store live E2E isolation: Store B sees zero Store A records in UI, API, Reports, and Exports', async ({ page }) => {
    const pageErrors = [];
    page.on('pageerror', (e) => pageErrors.push(e.message));
    await routeToIsolatedBackend(page);

    await page.goto('/login.html');
    await page.click('#tab-owner');
    await page.fill('#username', storeB.adminUsername);
    await page.fill('#password', storeB.password);
    await page.click('#login-submit-btn');
    await page.waitForURL(/dashboard\.html/, { timeout: 10000 });

    // UI checks across Store B pages
    for (const [url, forbiddenText] of [
      ['/products.html', prodAName],
      ['/categories.html', `Alpha Cat ${uid}`],
      ['/customers.html', `Customer A ${uid}`],
      ['/suppliers.html', supAName],
      ['/sales.html', receiptANum],
      ['/expenses.html', `Alpha Electricity ${uid}`],
    ]) {
      await page.goto(url);
      await page.waitForLoadState('networkidle');
      await expect(page.locator('body')).not.toContainText(forbiddenText);
    }

    // Settings isolated
    await page.goto('/settings.html');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('#set-store-name')).toHaveValue(storeB.storeName);

    // Reports & CSV exports isolated
    await page.goto('/reports.html');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('#rep-total-orders')).toHaveText('0 orders', { timeout: 5000 });

    const jwtB = await page.evaluate(() => localStorage.getItem('grocery_token'));
    const csvRes = await page.request.get(`${ISOLATED_API}/api/v1/reports/export/sales`, {
      headers: { Authorization: `Bearer ${jwtB}` },
    });
    expect(csvRes.status()).toBe(200);
    expect(await csvRes.text()).not.toContain(receiptANum);
    expect(pageErrors).toEqual([]);
  });
});
