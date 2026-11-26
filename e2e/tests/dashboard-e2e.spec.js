const { test, expect } = require('@playwright/test');

const API_BASE = 'http://localhost:8080/api/v1';

async function registerIsolatedStore(request, suffix) {
  const res = await request.post(`${API_BASE}/auth/register-store`, {
    data: {
      storeName: `E2E Dash Store ${suffix}`,
      storeCode: `ed${suffix}`,
      ownerName: 'E2E Owner',
      ownerEmail: `e2e_${suffix}@test.local`,
      city: 'Pune',
      password: 'Password123!',
      adminUsername: `e2eadm_${suffix}`
    }
  });
  expect(res.ok()).toBeTruthy();
  const body = await res.json();
  return body.data;
}

async function seedStoreData(request, token, storeId, tag) {
  const headers = { Authorization: `Bearer ${token}` };

  // Create active normal product, low-stock product, out-of-stock product, and XSS test product
  const p1Res = await request.post(`${API_BASE}/products`, {
    headers,
    data: {
      name: `Organic Rice ${tag}`,
      sku: `SKU-R-${tag}`,
      unit: 'kg',
      costPrice: 50,
      sellingPrice: 100,
      currentStock: 20,
      minStockLevel: 5,
      status: 'ACTIVE'
    }
  });
  expect(p1Res.ok()).toBeTruthy();
  const p1 = (await p1Res.json()).data || (await p1Res.json());

  const p2Res = await request.post(`${API_BASE}/products`, {
    headers,
    data: {
      name: `Low Stock Milk ${tag}`,
      sku: `SKU-M-${tag}`,
      unit: 'ltr',
      costPrice: 30,
      sellingPrice: 50,
      currentStock: 3,
      minStockLevel: 5,
      status: 'ACTIVE'
    }
  });
  expect(p2Res.ok()).toBeTruthy();

  const p3Res = await request.post(`${API_BASE}/products`, {
    headers,
    data: {
      name: `Out Stock Bread ${tag}`,
      sku: `SKU-B-${tag}`,
      unit: 'pkt',
      costPrice: 20,
      sellingPrice: 40,
      currentStock: 0,
      minStockLevel: 5,
      status: 'ACTIVE'
    }
  });
  expect(p3Res.ok()).toBeTruthy();

  // Checkout sale with XSS-like customer name to verify safe DOM rendering
  const saleRes = await request.post(`${API_BASE}/sales`, {
    headers,
    data: {
      customerName: `<img src=x onerror="window.__dashXss=true"> Buyer ${tag}`,
      subtotal: 300,
      grandTotal: 300,
      paymentMethod: 'CASH',
      items: [
        {
          productId: p1.id,
          productName: p1.name,
          quantity: 3,
          unitPrice: 100,
          lineTotal: 300
        }
      ]
    }
  });
  expect(saleRes.ok()).toBeTruthy();
  const sale = (await saleRes.json()).data;

  // Partial return of 50.0 (not restocked so COGS stays 150 -> Gross Profit = 250 - 150 = 100), then confirm
  const retRes = await request.post(`${API_BASE}/returns`, {
    headers,
    data: {
      saleId: sale.id,
      totalRefund: 50,
      refundMethod: 'CASH',
      notes: 'Damaged pack',
      items: [
        {
          saleItemId: sale.items[0].saleItemId,
          productId: p1.id,
          productName: p1.name,
          quantity: 1,
          refundAmount: 50,
          restocked: false
        }
      ]
    }
  });
  expect(retRes.ok()).toBeTruthy();
  const retId = (await retRes.json()).data.id;
  const confirmRes = await request.post(`${API_BASE}/returns/${retId}/confirm`, { headers });
  expect(confirmRes.ok()).toBeTruthy();

  return { p1, sale };
}

test.describe('Module 01 Dashboard E2E Verification', () => {
  test('DASH-UI-01..09: Live KPIs, Gross/Net Sales, Profit, Trend, Top Products, Recent Sales, Alerts, XSS Safety, Error Retry', async ({ page, request }) => {
    const suffix = Date.now().toString(36).slice(-6);
    const storeA = await registerIsolatedStore(request, `${suffix}a`);
    await seedStoreData(request, storeA.token, storeA.storeId, suffix);

    // Seed second store to verify zero cross-tenant leakage
    const storeB = await registerIsolatedStore(request, `${suffix}b`);
    await seedStoreData(request, storeB.token, storeB.storeId, `${suffix}B`);

    // Inject authenticated session for Store A
    await page.goto('/login.html');
    await page.evaluate((auth) => {
      localStorage.setItem('grocery_token', auth.token);
      localStorage.setItem('grocery_user', JSON.stringify({
        id: auth.userId || auth.id,
        username: auth.username,
        fullName: auth.fullName || 'E2E Owner',
        role: auth.role,
        storeId: auth.storeId,
        storeName: auth.storeName,
        storeCode: auth.storeCode
      }));
    }, storeA);

    await page.goto('/dashboard.html');

    await expect(page.locator('#kpi-today-sales')).toHaveText('₹250.00');
    await expect(page.locator('#kpi-today-gross-sales')).toContainText('₹300.00');
    await expect(page.locator('#kpi-today-orders')).toHaveText('1');
    await expect(page.locator('#kpi-today-aov')).toContainText('₹250.00');
    await expect(page.locator('#kpi-net-profit')).toHaveText('₹100.00');
    await expect(page.locator('#kpi-total-products')).toHaveText('3');
    await expect(page.locator('#kpi-low-stock')).toHaveText('1');
    await expect(page.locator('#kpi-out-of-stock')).toHaveText('1');

    // Trend chart, Top Products, Recent Sales, Alerts
    await expect(page.locator('#sales-trend-chart .trend-bar-col')).toHaveCount(7);
    await expect(page.locator('#top-products-list')).toContainText(`Organic Rice ${suffix}`);
    await expect(page.locator('#top-products-list')).not.toContainText(`${suffix}B`);
    await expect(page.locator('#recent-sales-tbody')).toContainText(`Buyer ${suffix}`);
    await expect(page.locator('#alerts-list')).toContainText(`Out Stock Bread ${suffix}`);
    await expect(page.locator('#alerts-list')).toContainText(`Low Stock Milk ${suffix}`);

    // XSS check: script payload rendered as text, never executed
    const xssFlag = await page.evaluate(() => Boolean(window.__dashXss));
    expect(xssFlag).toBe(false);

    // Period filter switch to Today (1 day)
    await page.selectOption('#date-range-preset', '1');
    await expect(page.locator('#sales-trend-chart .trend-bar-col')).toHaveCount(1);

    // Simulate network failure -> error banner + retry button -> recover
    let failOnce = true;
    await page.route('**/api/v1/reports/dashboard*', async (route) => {
      if (failOnce) {
        failOnce = false;
        await route.abort();
      } else {
        await route.continue();
      }
    });

    await page.selectOption('#date-range-preset', '7');
    await expect(page.locator('#dashboard-status-banner')).toHaveClass(/error/);
    await expect(page.locator('#dashboard-retry-btn')).toBeVisible();

    await page.click('#dashboard-retry-btn');
    await expect(page.locator('#dashboard-status-banner')).toHaveClass(/hidden/);
    await expect(page.locator('#sales-trend-chart .trend-bar-col')).toHaveCount(7);
  });

  test('DASH-UI-10..12: Responsive Desktop (1440), Tablet (768), and Mobile (375) Zero Overflow', async ({ page, request }) => {
    const suffix = Date.now().toString(36).slice(-6) + 'r';
    const store = await registerIsolatedStore(request, suffix);

    await page.goto('/login.html');
    await page.evaluate((auth) => {
      localStorage.setItem('grocery_token', auth.token);
      localStorage.setItem('grocery_user', JSON.stringify({
        id: auth.userId || auth.id,
        username: auth.username,
        fullName: auth.fullName || 'E2E Owner',
        role: auth.role,
        storeId: auth.storeId,
        storeName: auth.storeName,
        storeCode: auth.storeCode
      }));
    }, store);

    for (const vp of [
      { width: 1440, height: 900 },
      { width: 768, height: 1024 },
      { width: 375, height: 812 }
    ]) {
      await page.setViewportSize(vp);
      await page.goto('/dashboard.html');
      await expect(page.locator('#kpi-today-sales')).not.toHaveText('--');
      const hasOverflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
      expect(hasOverflow).toBe(false);
    }
  });

  test('Stage 2.2 UI-to-API-to-Dashboard: returns.html submits totalRefund & refundMethod, confirms refund, and updates dashboard', async ({ page, request }) => {
    const suffix = Date.now().toString(36).slice(-6) + 'u';
    const store = await registerIsolatedStore(request, suffix);
    const headers = { Authorization: `Bearer ${store.token}` };

    const pRes = await request.post(`${API_BASE}/products`, {
      headers,
      data: {
        name: `Return UI Tea ${suffix}`,
        sku: `SKU-T-${suffix}`,
        unit: 'pcs',
        costPrice: 60,
        sellingPrice: 100,
        currentStock: 10,
        minStockLevel: 2,
        status: 'ACTIVE'
      }
    });
    expect(pRes.ok()).toBeTruthy();
    const prod = (await pRes.json()).data || (await pRes.json());

    const saleRes = await request.post(`${API_BASE}/sales`, {
      headers,
      data: {
        customerName: `UI Return Buyer ${suffix}`,
        subtotal: 200,
        grandTotal: 200,
        paymentMethod: 'CASH',
        items: [{ productId: prod.id, productName: prod.name, quantity: 2, unitPrice: 100, lineTotal: 200 }]
      }
    });
    expect(saleRes.ok()).toBeTruthy();
    const sale = (await saleRes.json()).data;

    await page.goto('/login.html');
    await page.evaluate((auth) => {
      localStorage.setItem('grocery_token', auth.token);
      localStorage.setItem('grocery_user', JSON.stringify({
        id: auth.userId || auth.id,
        username: auth.username,
        fullName: auth.fullName || 'E2E Owner',
        role: auth.role,
        storeId: auth.storeId,
        storeName: auth.storeName,
        storeCode: auth.storeCode
      }));
    }, store);

    await page.goto('/returns.html');
    await page.fill('#return-receipt-input', sale.receiptNumber);
    await page.click('#btn-lookup-receipt');
    await expect(page.locator('#return-process-panel')).toBeVisible();

    await page.locator('.return-chk').first().check();
    await page.selectOption('#refund-mode', 'UPI');
    await page.click('#btn-process-refund');

    // Return created as PENDING with ₹100.00 (1 unit * 100)
    await expect(page.locator('#returns-history-body')).toContainText('PENDING');
    await expect(page.locator('#returns-history-body')).toContainText('₹100.00');

    // Before confirmation, dashboard Net Sales is still ₹200.00
    await page.goto('/dashboard.html');
    await expect(page.locator('#kpi-today-sales')).toHaveText('₹200.00');

    // Confirm refund on returns.html -> status becomes COMPLETED -> dashboard Net Sales becomes ₹100.00
    await page.goto('/returns.html');
    await page.click('.btn-confirm-refund');
    await expect(page.locator('#returns-history-body')).toContainText('COMPLETED');

    await page.goto('/dashboard.html');
    await expect(page.locator('#kpi-today-sales')).toHaveText('₹100.00');
    await expect(page.locator('#kpi-today-gross-sales')).toContainText('₹200.00');
  });
});
