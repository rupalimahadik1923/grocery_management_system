const { test, expect } = require('@playwright/test');

const TEST_API_ORIGIN = 'http://127.0.0.1:8081';

async function routeToTestBackend(page) {
  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url().replace(/^https?:\/\/[^/]+/, TEST_API_ORIGIN);
    await route.continue({ url });
  });
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

test('Phase 3 — End-to-End Inventory: Create Product -> Sell to 0 -> Restock from Products -> Verify Batch & Ledger -> Adjust -> Sell Again', async ({ page }) => {
  const uid = Date.now().toString(36).slice(-6);
  const prodName = `E2E Basmati ${uid}`;
  const prodSku = `E2E-SKU-${uid}`.toUpperCase();
  const supName = `E2E Farms ${uid}`;

  await routeToTestBackend(page);

  // 1. Register isolated store
  await registerStoreViaUI(page, {
    storeName: `Inv E2E Store ${uid}`,
    storeCode: `inve2e${uid}`,
    city: 'Bengaluru',
    ownerName: 'E2E Owner',
    ownerEmail: `inv_e2e_${uid}@test.local`,
    adminUsername: `inv_adm_${uid}`,
    password: 'StrongPass123!',
  });

  // 2. Create Supplier
  await page.goto('/suppliers.html');
  await page.waitForLoadState('networkidle');
  await page.click('#btn-add-supplier');
  await page.fill('#sup-name', supName);
  await page.fill('#sup-code', `SUP-${uid}`.toUpperCase());
  await page.fill('#sup-phone', '9111122233');
  await page.click('#btn-save-sup');
  await expect(page.locator('#suppliers-table-body')).toContainText(supName, { timeout: 5000 });

  // 2b. Create Category (required by product form)
  await page.goto('/categories.html');
  await page.waitForLoadState('networkidle');
  await page.click('#btn-add-category');
  await page.fill('#cat-name', `E2E Grains ${uid}`);
  await page.fill('#cat-slug', `e2egrains-${uid}`);
  await page.click('#btn-save-cat');
  await expect(page.locator('body')).toContainText(`E2E Grains ${uid}`, { timeout: 5000 });

  // 3. Create Product with Initial Stock = 2
  await page.goto('/products.html');
  await page.waitForLoadState('networkidle');
  await page.click('#btn-add-product');
  await page.fill('#prod-name', prodName);
  await page.fill('#prod-sku', prodSku);
  await page.selectOption('#prod-category', { index: 1 });
  await page.fill('#prod-cost', '40');
  await page.fill('#prod-price', '60');
  await page.fill('#prod-stock', '2');
  await page.fill('#prod-min-stock', '5');
  const [createProdRes] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/v1/products') && r.request().method() === 'POST'),
    page.click('#btn-save-prod'),
  ]);
  expect(createProdRes.status()).toBe(200);
  await expect(page.locator('#products-table-body')).toContainText(prodName, { timeout: 5000 });

  // 4. Sell all 2 units in POS so stock drops to 0
  await page.goto('/pos.html');
  await page.waitForLoadState('networkidle');
  const card = page.locator('.pos-product-card', { hasText: prodName });
  await expect(card).toBeVisible({ timeout: 5000 });
  await card.click();
  await page.click('.pos-qty-plus, button:has-text("+")').catch(() => {});
  await page.click('#btn-open-tender');
  const [saleRes1] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/v1/sales') && r.request().method() === 'POST'),
    page.click('#btn-complete-payment'),
  ]);
  expect(saleRes1.status()).toBe(200);
  await expect(page.locator('#receipt-modal')).toHaveClass(/active/);

  // 5. Return to Products -> verify Out of Stock -> click Restock row action
  await page.goto('/products.html');
  await page.waitForLoadState('networkidle');
  const prodRow = page.locator('#products-table-body tr', { hasText: prodName });
  await expect(prodRow).toContainText('Out of Stock', { timeout: 5000 });
  await prodRow.locator('a[title="Restock Product"]').click();

  // 6. Verify deep-link to inventory.html?sku=...&restock=1 auto-opens Restock modal
  await page.waitForURL(/inventory\.html/, { timeout: 10000 });
  await expect(page.locator('#restock-modal')).toHaveClass(/active/, { timeout: 5000 });

  await page.fill('#restock-qty', '20');
  const supOptionVal = await page.locator('#restock-supplier option', { hasText: supName }).getAttribute('value');
  if (supOptionVal) {
    await page.selectOption('#restock-supplier', supOptionVal);
  }
  await page.fill('#restock-notes', 'E2E restock after stockout');

  const [restockRes] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/v1/inventory/restock') && r.request().method() === 'POST'),
    page.click('#btn-submit-restock'),
  ]);
  expect(restockRes.status()).toBe(200);
  await expect(page.locator('#restock-modal')).not.toHaveClass(/active/);

  // 7. Verify Stock Levels table shows 20 units and Healthy Stock
  const stockRow = page.locator('#stock-table-body tr', { hasText: prodName });
  await expect(stockRow).toContainText('20', { timeout: 5000 });
  await expect(stockRow).toContainText('Healthy Stock');

  // 8. Verify Batch & Expiry Tracker (FEFO) shows 20 / 20, ₹40.00, supplier, and No Expiry
  await page.click('#tab-batches-btn');
  const batchRow = page.locator('#batches-table-body tr', { hasText: prodName });
  await expect(batchRow).toContainText('20 / 20', { timeout: 5000 });
  await expect(batchRow).toContainText('40.00');
  await expect(batchRow).toContainText(supName);
  await expect(batchRow).toContainText('No Expiry');

  // 9. Verify Audit Ledger shows RESTOCK +20, balance 20
  await page.click('#tab-movements-btn');
  const txRow = page.locator('#tx-table-body tr', { hasText: 'RESTOCK' }).first();
  await expect(txRow).toContainText(prodName, { timeout: 5000 });
  await expect(txRow).toContainText('+20');
  await expect(txRow).toContainText('20');

  // 10. Perform Quick Stock Adjustment (-5 DAMAGE) and verify stock = 15
  await page.click('#tab-stock-btn');
  await stockRow.locator('.btn-quick-adj').click();
  await expect(page.locator('#adjustment-modal')).toHaveClass(/active/);
  await page.selectOption('#adj-type', 'DAMAGE');
  await page.fill('#adj-qty', '-5');
  await page.fill('#adj-notes', 'E2E damaged bag');
  const [adjRes] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/v1/inventory/adjustments') && r.request().method() === 'POST'),
    page.click('#btn-submit-adj'),
  ]);
  expect(adjRes.status()).toBe(200);
  await expect(stockRow).toContainText('15', { timeout: 5000 });

  // 11. Sell restocked product again in POS
  await page.goto('/pos.html');
  await page.waitForLoadState('networkidle');
  const card2 = page.locator('.pos-product-card', { hasText: prodName });
  await expect(card2).toBeVisible({ timeout: 5000 });
  await card2.click();
  await page.click('#btn-open-tender');
  const [saleRes2] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/v1/sales') && r.request().method() === 'POST'),
    page.click('#btn-complete-payment'),
  ]);
  expect(saleRes2.status()).toBe(200);
});
