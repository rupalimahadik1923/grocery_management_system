const { test, expect } = require('@playwright/test');

test.describe('Authentication & Authorization E2E', () => {
  test('Login page loads with online status and zero console errors', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (err) => errors.push(err.message));

    await page.goto('/login.html');
    await expect(page).toHaveTitle(/Staff & Admin Login/);
    await expect(page.locator('#live-system-status')).toContainText('Cloud Online', { timeout: 10000 });
    expect(errors).toEqual([]);
  });

  test('Tab switching, store verification, password toggle, and error recovery', async ({ page }) => {
    await page.goto('/login.html');

    // Default is Staff tab
    await expect(page.locator('#store-code-group')).toBeVisible();
    await expect(page.locator('#auth-panel-title')).toHaveText('Staff Terminal Login');

    // Store verification: valid & invalid
    await page.fill('#store-code', 'freshgrocery');
    await expect(page.locator('#store-verification-badge')).toContainText('FreshGrocery Supermarket', { timeout: 5000 });
    await page.fill('#store-code', 'nonexistent_store_999');
    await expect(page.locator('#store-verification-badge')).toContainText('not found', { timeout: 5000 });
    await page.fill('#store-code', 'freshgrocery');

    // Password toggle
    await expect(page.locator('#password')).toHaveAttribute('type', 'password');
    await page.click('#toggle-password-btn');
    await expect(page.locator('#password')).toHaveAttribute('type', 'text');
    await page.click('#toggle-password-btn');
    await expect(page.locator('#password')).toHaveAttribute('type', 'password');

    // Empty submit error
    await page.click('#login-submit-btn');
    await expect(page.locator('#error-box')).toBeVisible();
    await expect(page.locator('#error-box')).toContainText('Please enter both username and password');

    // Wrong password error & button recovery
    await page.fill('#username', 'cashier');
    await page.fill('#password', 'wrongpass');
    await page.click('#login-submit-btn');
    await expect(page.locator('#error-box')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('#login-submit-btn')).toBeEnabled();

    // Switch to Store Admin tab -> button text updates properly
    await page.click('#tab-owner');
    await expect(page.locator('#store-code-group')).toBeHidden();
    await expect(page.locator('#auth-panel-title')).toHaveText('Store Admin Login');
    await expect(page.locator('#submit-btn-text')).toHaveText('Access Store Dashboard');

    // Non-admin (cashier) blocked on Store Admin tab
    await page.fill('#username', 'cashier');
    await page.fill('#password', 'cashier123');
    await page.click('#login-submit-btn');
    await expect(page.locator('#error-box')).toBeVisible({ timeout: 5000 });
  });

  test('Registration modal validations and duplicate store check (no live DB mutation)', async ({ page }) => {
    await page.goto('/login.html');
    await page.click('#btn-open-register');
    await expect(page.locator('#register-store-modal')).toHaveClass(/active/);

    // Auto-slug from store name
    await page.fill('#reg-store-name', 'Metro Fresh Demo');
    await expect(page.locator('#reg-store-code')).toHaveValue('metrofreshdemo');

    // Duplicate store check
    await page.fill('#reg-store-code', 'freshgrocery');
    await expect(page.locator('#reg-store-code-feedback')).toContainText('already registered', { timeout: 5000 });

    // Advance to Step 2
    await page.fill('#reg-store-code', 'uniquedemo99');
    await page.fill('#reg-city', 'Bengaluru');
    await page.click('#btn-reg-next');

    // Password mismatch & match feedback
    await page.fill('#reg-password', 'Strong123');
    await page.fill('#reg-password-confirm', 'Mismatch');
    await expect(page.locator('#reg-password-feedback')).toContainText('do not match');
    await page.fill('#reg-password-confirm', 'Strong123');
    await expect(page.locator('#reg-password-feedback')).toContainText('Passwords match');

    // Invalid email validation block
    await page.fill('#reg-owner-name', 'Test Owner');
    await page.fill('#reg-owner-email', 'invalid-email');
    await page.click('#btn-submit-register');
    await expect(page.locator('#register-error-box')).toContainText('valid Owner Email');

    // Close modal
    await page.click('#btn-close-register');
    await expect(page.locator('#register-store-modal')).not.toHaveClass(/active/);
  });

  test('Staff Cashier login, Remember Username, authenticated redirect, POS access, and Logout', async ({ page }) => {
    await page.goto('/login.html');
    await page.fill('#store-code', 'freshgrocery');
    await page.fill('#username', 'cashier');
    await page.fill('#password', 'cashier123');
    await page.check('#remember-me');
    await page.click('#login-submit-btn');

    await page.waitForURL(/pos\.html/, { timeout: 10000 });
    const token = await page.evaluate(() => localStorage.getItem('grocery_token'));
    const userRaw = await page.evaluate(() => localStorage.getItem('grocery_user'));
    const rememberedUser = await page.evaluate(() => localStorage.getItem('freshgrocery_remembered_username'));
    expect(token).toBeTruthy();
    expect(JSON.parse(userRaw).role).toBe('CASHIER');
    expect(rememberedUser).toBe('cashier');

    // Visiting login.html while authenticated redirects back to pos.html
    await page.goto('/login.html');
    await page.waitForURL(/pos\.html/, { timeout: 10000 });

    // Logout clears token & user and returns to login.html with remembered username
    await page.click('#logout-btn');
    await page.waitForURL(/login\.html/, { timeout: 10000 });
    const afterLogoutToken = await page.evaluate(() => localStorage.getItem('grocery_token'));
    expect(afterLogoutToken).toBeNull();
    await expect(page.locator('#username')).toHaveValue('cashier');
  });

  test('Unauthenticated access to dashboard.html and pos.html redirects to login.html', async ({ page }) => {
    await page.goto('/dashboard.html');
    await page.waitForURL(/login\.html/, { timeout: 10000 });

    await page.goto('/pos.html');
    await page.waitForURL(/login\.html/, { timeout: 10000 });
  });

  test('Store Admin login redirects to dashboard.html and renders ADMIN sidebar', async ({ page }) => {
    await page.goto('/login.html');
    await page.click('#tab-owner');
    await page.fill('#username', 'admin');
    await page.fill('#password', 'admin123');
    await page.click('#login-submit-btn');

    await page.waitForURL(/dashboard\.html/, { timeout: 10000 });
    await expect(page.locator('#sidebar')).toContainText('User Management');
    await expect(page.locator('#sidebar')).toContainText('Store Settings');

    await page.click('#logout-btn');
    await page.waitForURL(/login\.html/, { timeout: 10000 });
  });

  test('Admin UI-to-Backend E2E: Store Admin login -> Dashboard -> Users -> Settings with network & console checks', async ({ page }) => {
    const consoleErrors = [];
    const failedApiCalls = [];
    const apiStatuses = [];

    page.on('pageerror', (err) => consoleErrors.push(err.message));
    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text());
    });
    page.on('response', (res) => {
      if (res.url().includes('/api/v1/')) {
        apiStatuses.push(`${res.request().method()} ${new URL(res.url()).pathname} -> ${res.status()}`);
        if (res.status() >= 400) {
          failedApiCalls.push(`${res.request().method()} ${res.url()} -> ${res.status()}`);
        }
      }
    });

    await page.setViewportSize({ width: 1366, height: 768 });
    await page.goto('/login.html');
    await page.click('#tab-owner');
    await page.fill('#username', 'admin');
    await page.fill('#password', 'admin123');

    const [loginRes] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/v1/auth/login') && r.request().method() === 'POST'),
      page.click('#login-submit-btn'),
    ]);
    expect(loginRes.status()).toBe(200);
    const loginBody = await loginRes.json();
    expect(loginBody.role).toBe('ADMIN');
    expect(loginBody.token).toBeTruthy();

    await page.waitForURL(/dashboard\.html/, { timeout: 10000 });
    await page.waitForLoadState('networkidle');
    await expect(page.locator('#sidebar')).toContainText('User Management');
    await expect(page.locator('#sidebar')).toContainText('Store Settings');
    await page.screenshot({ path: 'test-results/admin-dashboard.png' });

    // Navigate to Admin-only User Management page
    await page.click('a[href="users.html"]');
    await page.waitForURL(/users\.html/, { timeout: 10000 });
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: 'test-results/admin-users.png' });

    // Navigate to Admin-only Store Settings page
    await page.click('a[href="settings.html"]');
    await page.waitForURL(/settings\.html/, { timeout: 10000 });
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: 'test-results/admin-settings.png' });

    console.log('Admin API Calls:', apiStatuses);
    expect(failedApiCalls).toEqual([]);
    expect(consoleErrors).toEqual([]);

    await page.click('#logout-btn');
    await page.waitForURL(/login\.html/, { timeout: 10000 });
  });
});
