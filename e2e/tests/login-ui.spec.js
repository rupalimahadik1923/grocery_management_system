const { test, expect } = require('@playwright/test');

test.describe('Login & Registration UI/UX Verification', () => {
  test('Desktop (1440x900) layout, SVG icons, accessibility, and zero horizontal overflow', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/login.html');

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    expect(overflow).toBe(false);

    // #error-box must be hidden on initial load (no blank pink bar)
    await expect(page.locator('#error-box')).toBeHidden();

    // Accessible error box & portal tabs
    await expect(page.locator('#error-box')).toHaveAttribute('role', 'alert');
    await expect(page.locator('#tab-staff')).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('#tab-owner')).toHaveAttribute('aria-selected', 'false');

    // Inline SVG icons present in brand & tabs
    expect(await page.locator('.hero-brand-logo svg').count()).toBeGreaterThan(0);
    expect(await page.locator('#tab-staff svg').count()).toBeGreaterThan(0);
    expect(await page.locator('#tab-owner svg').count()).toBeGreaterThan(0);

    await page.screenshot({ path: 'test-results/ui-desktop-login.png', fullPage: false });
  });

  test('Mobile (375x812) responsive layout collapses hero cleanly with no overflow', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto('/login.html');

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    expect(overflow).toBe(false);

    // Auth panel and login button should be visible and unclipped on mobile
    await expect(page.locator('#tab-staff')).toBeVisible();
    const tabBox = await page.locator('#tab-staff').boundingBox();
    expect(tabBox.y).toBeGreaterThanOrEqual(0);
    await expect(page.locator('#login-submit-btn')).toBeVisible();

    await page.screenshot({ path: 'test-results/ui-mobile-login.png', fullPage: false });
  });

  test('Inline field validation highlights missing inputs and clears on typing', async ({ page }) => {
    await page.goto('/login.html');

    // Empty username submit highlights #username
    await page.click('#login-submit-btn');
    await expect(page.locator('#username')).toHaveClass(/has-error/);
    await expect(page.locator('#error-box')).toBeVisible();

    // Typing clears error highlight
    await page.fill('#username', 'cashier');
    await expect(page.locator('#username')).not.toHaveClass(/has-error/);

    // Empty password submit highlights #password
    await page.click('#login-submit-btn');
    await expect(page.locator('#password')).toHaveClass(/has-error/);

    // Switching to Store Admin tab updates aria-selected and role hint
    await page.click('#tab-owner');
    await expect(page.locator('#tab-owner')).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('#tab-staff')).toHaveAttribute('aria-selected', 'false');
    await expect(page.locator('#portal-role-hint')).toContainText('Store Owners');
  });

  test('2-Step Registration Wizard navigation, Step 1 validation, and Password Strength Meter', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 700 });
    await page.goto('/login.html');

    await page.click('#btn-open-register');
    await expect(page.locator('#register-store-modal')).toHaveClass(/active/);

    // Verify modal and Next button fit cleanly within 1280x700 laptop viewport without scrolling
    const modalBox = await page.locator('#register-store-modal .modal-card').boundingBox();
    expect(modalBox.width).toBeGreaterThanOrEqual(560);
    expect(modalBox.height).toBeLessThanOrEqual(700 * 0.90);
    const nextBtnBox = await page.locator('#btn-reg-next').boundingBox();
    expect(nextBtnBox.y + nextBtnBox.height).toBeLessThanOrEqual(700);

    // Step 1 visible, Step 2 hidden
    await expect(page.locator('#reg-step-1')).toBeVisible();
    await expect(page.locator('#reg-step-2')).toBeHidden();
    await expect(page.locator('#reg-step-pill-1')).toHaveClass(/is-active/);

    // Clicking Next with empty Step 1 fields shows validation error and stays on Step 1
    await page.click('#btn-reg-next');
    await expect(page.locator('#register-error-box')).toBeVisible();
    await expect(page.locator('#reg-step-1')).toBeVisible();

    // Duplicate storeCode on Step 1 blocks Next ->
    await page.fill('#reg-store-name', 'Fresh Duplicate');
    await page.fill('#reg-store-code', 'freshgrocery');
    await page.fill('#reg-city', 'Bengaluru');
    await page.click('#btn-reg-next');
    await expect(page.locator('#register-error-box')).toContainText('already registered', { timeout: 5000 });
    await expect(page.locator('#reg-step-1')).toBeVisible();

    // Fill valid Step 1 and press Enter in #reg-city -> advances cleanly to Step 2
    await page.fill('#reg-store-code', '');
    await page.fill('#reg-store-name', 'Emerald Fresh Mart');
    await expect(page.locator('#reg-store-code')).toHaveValue('emeraldfreshmart');
    await page.fill('#reg-city', 'Bengaluru');
    await page.screenshot({ path: 'test-results/ui-register-step1.png' });

    await page.locator('#reg-city').press('Enter');
    await expect(page.locator('#reg-step-1')).toBeHidden();
    await expect(page.locator('#reg-step-2')).toBeVisible();
    await expect(page.locator('#reg-step-pill-2')).toHaveClass(/is-active/);

    // Verify Submit button is pinned & visible inside 1280x700 viewport without scrolling
    const submitRegBox = await page.locator('#btn-submit-register').boundingBox();
    expect(submitRegBox.y + submitRegBox.height).toBeLessThanOrEqual(700);

    // Password strength meter & eye toggles on Step 2
    await page.fill('#reg-password', '123');
    await expect(page.locator('#reg-password-strength-label')).toContainText('Weak');
    await page.fill('#reg-password', 'StrongPass123!');
    await expect(page.locator('#reg-password-strength-label')).toContainText('Strong');

    await expect(page.locator('#toggle-reg-password-btn')).toBeVisible();
    await page.click('#toggle-reg-password-btn');
    await expect(page.locator('#reg-password')).toHaveAttribute('type', 'text');
    await page.click('#toggle-reg-password-btn');
    await expect(page.locator('#reg-password')).toHaveAttribute('type', 'password');

    await expect(page.locator('#toggle-reg-password-confirm-btn')).toBeVisible();
    await page.fill('#reg-password-confirm', 'StrongPass123!');
    await page.click('#toggle-reg-password-confirm-btn');
    await expect(page.locator('#reg-password-confirm')).toHaveAttribute('type', 'text');
    await page.screenshot({ path: 'test-results/ui-register-step2.png' });

    // Back button returns to Step 1 and preserves Store Name
    await page.click('#btn-reg-back');
    await expect(page.locator('#reg-step-1')).toBeVisible();
    await expect(page.locator('#reg-store-name')).toHaveValue('Emerald Fresh Mart');

    await page.click('#btn-close-register');
    await expect(page.locator('#register-store-modal')).not.toHaveClass(/active/);
  });

  test('Staff & Store Admin invalid credential outcomes, alignment, and error banner visibility', async ({ page }) => {
    await page.setViewportSize({ width: 1366, height: 768 });
    await page.goto('/login.html');

    // 1. Staff Login invalid password -> shows exact backend error message
    await page.fill('#store-code', 'freshgrocery');
    await page.fill('#username', 'cashier');
    await page.fill('#password', 'wrongpass123');
    await page.click('#login-submit-btn');
    await expect(page.locator('#error-box')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('#error-box')).toContainText(/Invalid username or password/i);
    await page.screenshot({ path: 'test-results/ui-staff-invalid.png' });

    // 2. Switch to Store Admin -> error box clears, Store Code hides, invalid admin password shows error
    await page.click('#tab-owner');
    await expect(page.locator('#error-box')).toBeHidden();
    await expect(page.locator('#store-code-group')).toBeHidden();
    await expect(page.locator('#portal-role-hint')).toContainText('Store Owners & Store Admins Only');
    await page.fill('#username', 'admin');
    await page.fill('#password', 'wrongadminpass');
    await page.click('#login-submit-btn');
    await expect(page.locator('#error-box')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('#error-box')).toContainText(/Invalid username or password/i);
    await page.screenshot({ path: 'test-results/ui-admin-invalid.png' });
  });
});
