import { Auth } from './auth.js';

const API_BASE_URL = window.location.origin.includes(':8080')
  ? '/api/v1'
  : 'http://localhost:8080/api/v1';

document.addEventListener('DOMContentLoaded', () => {
  // 1. Session Redirect Guard
  if (Auth.isAuthenticated()) {
    const user = Auth.getUser();
    window.location.href = (user && user.role === 'CASHIER') ? 'pos.html' : 'dashboard.html';
    return;
  }

  // 2. DOM Elements
  const form = document.getElementById('login-form');
  const storeCodeGroup = document.getElementById('store-code-group');
  const storeCodeInput = document.getElementById('store-code');
  const storeBadge = document.getElementById('store-verification-badge');
  const storeBadgeText = document.getElementById('store-verification-text');
  const usernameInput = document.getElementById('username');
  const usernameLabel = document.getElementById('username-label');
  const passwordInput = document.getElementById('password');
  const rememberCheckbox = document.getElementById('remember-me');
  const submitBtn = document.getElementById('login-submit-btn');
  const submitBtnText = document.getElementById('submit-btn-text');
  const errorBox = document.getElementById('error-box');
  const capsLockWarning = document.getElementById('caps-lock-warning');
  const togglePasswordBtn = document.getElementById('toggle-password-btn');
  const authCard = document.querySelector('.auth-card-inner');
  const authTitle = document.getElementById('auth-panel-title');
  const authSubtitle = document.getElementById('auth-panel-subtitle');
  const tabStaff = document.getElementById('tab-staff');
  const tabOwner = document.getElementById('tab-owner');
  const portalRoleHint = document.getElementById('portal-role-hint');

  // Register Modal Elements
  const modalRegister = document.getElementById('register-store-modal');
  const regModalBody = document.getElementById('register-modal-body');
  const btnOpenRegister = document.getElementById('btn-open-register');
  const btnCloseRegister = document.getElementById('btn-close-register');
  const btnCancelRegister = document.getElementById('btn-cancel-register');
  const btnRegNext = document.getElementById('btn-reg-next');
  const btnRegBack = document.getElementById('btn-reg-back');
  const regStep1 = document.getElementById('reg-step-1');
  const regStep2 = document.getElementById('reg-step-2');
  const regStepPill1 = document.getElementById('reg-step-pill-1');
  const regStepPill2 = document.getElementById('reg-step-pill-2');
  const regPasswordStrength = document.getElementById('reg-password-strength');
  const regPasswordStrengthFill = document.getElementById('reg-password-strength-fill');
  const regPasswordStrengthLabel = document.getElementById('reg-password-strength-label');
  const btnCopyStoreCode = document.getElementById('btn-copy-store-code');
  const btnCopyUsername = document.getElementById('btn-copy-username');
  const formRegister = document.getElementById('register-store-form');
  const regStoreName = document.getElementById('reg-store-name');
  const regStoreCode = document.getElementById('reg-store-code');
  const regStoreCodeFeedback = document.getElementById('reg-store-code-feedback');
  const regOwnerName = document.getElementById('reg-owner-name');
  const regOwnerEmail = document.getElementById('reg-owner-email');
  const regPhone = document.getElementById('reg-phone');
  const regCity = document.getElementById('reg-city');
  const regAddress = document.getElementById('reg-address');
  const regAdminUsername = document.getElementById('reg-admin-username');
  const regPassword = document.getElementById('reg-password');
  const regPasswordConfirm = document.getElementById('reg-password-confirm');
  const regPasswordFeedback = document.getElementById('reg-password-feedback');
  const registerErrorBox = document.getElementById('register-error-box');
  const regBottomErrorBox = document.getElementById('register-bottom-error-box');
  const btnSubmitRegister = document.getElementById('btn-submit-register');
  const modalSuccess = document.getElementById('register-success-modal');
  const btnProceedDashboard = document.getElementById('btn-proceed-dashboard');
  const btnCloseSuccess = document.getElementById('btn-close-success');

  let currentPortalMode = 'STAFF'; // 'STAFF' or 'OWNER'

  // 3. Tab Switching Logic
  tabStaff.addEventListener('click', () => switchPortalMode('STAFF'));
  tabOwner.addEventListener('click', () => switchPortalMode('OWNER'));

  function switchPortalMode(mode) {
    currentPortalMode = mode;
    hideError();

    if (mode === 'STAFF') {
      tabStaff.classList.add('active-tab');
      tabOwner.classList.remove('active-tab');
      tabStaff.setAttribute('aria-selected', 'true');
      tabOwner.setAttribute('aria-selected', 'false');
      if (portalRoleHint) portalRoleHint.textContent = 'Cashiers, Managers & Inventory Staff';
      storeCodeGroup.style.display = 'block';
      authTitle.textContent = 'Staff Terminal Login';
      authSubtitle.textContent = 'Enter your store code and staff credentials (or admin) to access your terminal.';
      usernameLabel.textContent = 'Staff Username (or Admin)';
      usernameInput.placeholder = 'Enter staff username (e.g. cashier or admin)';
      submitBtnText.textContent = 'Sign In to Terminal';
      validateStoreCode();
    } else {
      tabOwner.classList.add('active-tab');
      tabStaff.classList.remove('active-tab');
      tabOwner.setAttribute('aria-selected', 'true');
      tabStaff.setAttribute('aria-selected', 'false');
      if (portalRoleHint) portalRoleHint.textContent = 'Store Owners & Store Admins Only';
      storeCodeGroup.style.display = 'none';
      authTitle.textContent = 'Store Admin Login';
      authSubtitle.textContent = 'Sign in with your admin username, owner email, or store code.';
      usernameLabel.textContent = 'Admin Username / Email / Store Code';
      usernameInput.placeholder = 'e.g. admin, owner@store.com, or storecode';
      submitBtnText.textContent = 'Access Store Dashboard';
    }
  }

  // 4. Real-time Store Code Verification
  let storeCheckTimeout = null;
  storeCodeInput.addEventListener('input', () => {
    clearTimeout(storeCheckTimeout);
    storeCheckTimeout = setTimeout(validateStoreCode, 300);
  });

  async function validateStoreCode() {
    if (currentPortalMode !== 'STAFF') return;
    const code = storeCodeInput.value.trim().toLowerCase();
    if (!code) {
      storeBadge.style.display = 'none';
      return;
    }

    try {
      const res = await fetch(`${API_BASE_URL}/auth/store/${encodeURIComponent(code)}`);
      const data = await res.json();
      if (data && data.exists) {
        storeBadge.className = 'store-verification-pill verified';
        storeBadgeText.textContent = `Store: ${data.storeName} (${data.city || 'Active'})`;
        storeBadge.style.display = 'flex';
      } else {
        storeBadge.className = 'store-verification-pill not-found';
        storeBadgeText.textContent = `Store code '${code}' not found`;
        storeBadge.style.display = 'flex';
      }
    } catch {
      storeBadge.style.display = 'none';
    }
  }
  validateStoreCode();

  // 5. Load Remembered Username & Store Code
  const savedStoreCode = localStorage.getItem('freshgrocery_remembered_store') || localStorage.getItem('freshgrocery_last_store');
  const savedUsername = localStorage.getItem('freshgrocery_remembered_username');
  if (savedStoreCode) {
    storeCodeInput.value = savedStoreCode;
    validateStoreCode();
  }
  if (savedUsername) {
    usernameInput.value = savedUsername;
    rememberCheckbox.checked = true;
    passwordInput.focus();
  }

  const toggleRegPasswordBtn = document.getElementById('toggle-reg-password-btn');
  const toggleRegPasswordConfirmBtn = document.getElementById('toggle-reg-password-confirm-btn');

  // 6. Password Visibility Toggle (shared helper for Login & Registration)
  const eyeOpenSvg = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>`;
  const eyeOffSvg = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line></svg>`;

  function bindPasswordToggle(inputEl, btnEl) {
    if (!inputEl || !btnEl) return;
    btnEl.addEventListener('click', () => {
      const show = inputEl.type === 'password';
      inputEl.type = show ? 'text' : 'password';
      btnEl.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
      btnEl.innerHTML = show ? eyeOffSvg : eyeOpenSvg;
    });
  }

  bindPasswordToggle(passwordInput, togglePasswordBtn);
  bindPasswordToggle(regPassword, toggleRegPasswordBtn);
  bindPasswordToggle(regPasswordConfirm, toggleRegPasswordConfirmBtn);

  // 7. Caps Lock Detection
  const checkCapsLock = (e) => {
    if (e.getModifierState && e.getModifierState('CapsLock')) {
      capsLockWarning.style.display = 'flex';
    } else {
      capsLockWarning.style.display = 'none';
    }
  };
  passwordInput.addEventListener('keydown', checkCapsLock);
  passwordInput.addEventListener('keyup', checkCapsLock);
  passwordInput.addEventListener('blur', () => {
    capsLockWarning.style.display = 'none';
  });

  // Clear inline errors on input
  [storeCodeInput, usernameInput, passwordInput].forEach((input) => {
    input.addEventListener('input', () => input.classList.remove('has-error'));
  });

  // 8. Main Form Submission Handler
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideError();
    [storeCodeInput, usernameInput, passwordInput].forEach((el) => el.classList.remove('has-error'));

    const username = usernameInput.value.trim();
    const password = passwordInput.value;
    const storeCode = (currentPortalMode === 'STAFF') ? storeCodeInput.value.trim().toLowerCase() : null;

    if (currentPortalMode === 'STAFF' && !storeCode) {
      storeCodeInput.classList.add('has-error');
      storeCodeInput.focus();
      showError('Please enter your Store Code.');
      return;
    }
    if (!username) {
      usernameInput.classList.add('has-error');
      usernameInput.focus();
      showError('Please enter both username and password.');
      return;
    }
    if (!password) {
      passwordInput.classList.add('has-error');
      passwordInput.focus();
      showError('Please enter both username and password.');
      return;
    }

    setLoading(true);

    try {
      const payload = { username, password, portalMode: currentPortalMode };
      if (storeCode) payload.storeCode = storeCode;

      const res = await fetch(`${API_BASE_URL}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || 'Authentication failed. Please verify credentials.');
      }

      // Save token and session
      localStorage.setItem('grocery_token', data.token);
      localStorage.setItem('grocery_user', JSON.stringify({
        id: data.id,
        username: data.username,
        fullName: data.fullName,
        email: data.email,
        role: data.role,
        storeId: data.storeId,
        storeCode: data.storeCode,
        storeName: data.storeName
      }));

      // Handle "Remember Me"
      if (rememberCheckbox.checked) {
        localStorage.setItem('freshgrocery_remembered_username', username);
        if (storeCode) localStorage.setItem('freshgrocery_remembered_store', storeCode);
      } else {
        localStorage.removeItem('freshgrocery_remembered_username');
        localStorage.removeItem('freshgrocery_remembered_store');
      }

      submitBtn.innerHTML = `<span>✓</span> <span>Authorized! Launching...</span>`;
      submitBtn.style.background = '#059669';

      setTimeout(() => {
        window.location.href = (data.role === 'CASHIER') ? 'pos.html' : 'dashboard.html';
      }, 400);

    } catch (err) {
      setLoading(false);
      showError(err.message || 'Invalid credentials or store code. Please try again.');
      triggerShake();
    }
  });

  // 9. Register New Store 2-Step Wizard Logic
  const resetRegisterBtn = () => {
    btnSubmitRegister.disabled = false;
    btnSubmitRegister.textContent = 'Create Store & Launch';
  };

  const goToRegStep = (step) => {
    clearRegisterErrors();
    if (step === 1) {
      if (regStep1) regStep1.style.display = 'block';
      if (regStep2) regStep2.style.display = 'none';
      if (regStepPill1) { regStepPill1.classList.add('is-active'); regStepPill1.classList.remove('is-done'); }
      if (regStepPill2) regStepPill2.classList.remove('is-active');
      if (btnRegBack) btnRegBack.style.display = 'none';
      if (btnRegNext) btnRegNext.style.display = 'inline-flex';
      btnSubmitRegister.style.display = 'none';
    } else {
      if (regStep1) regStep1.style.display = 'none';
      if (regStep2) regStep2.style.display = 'block';
      if (regStepPill1) { regStepPill1.classList.remove('is-active'); regStepPill1.classList.add('is-done'); }
      if (regStepPill2) regStepPill2.classList.add('is-active');
      if (btnRegBack) btnRegBack.style.display = 'inline-flex';
      if (btnRegNext) btnRegNext.style.display = 'none';
      btnSubmitRegister.style.display = 'inline-flex';
      regOwnerName.focus();
    }
  };

  if (btnRegNext) {
    btnRegNext.addEventListener('click', async () => {
      clearRegisterErrors();
      const storeName = regStoreName.value.trim();
      const storeCode = regStoreCode.value.trim().toLowerCase();
      const city = regCity.value.trim();
      if (!storeName) {
        showRegisterError('Please enter your Store Name.', regStoreName);
        return;
      }
      if (!storeCode || storeCode.length < 2) {
        showRegisterError('Store Code must be at least 2 alphanumeric characters.', regStoreCode);
        return;
      }
      if (!city) {
        showRegisterError('Please enter the City.', regCity);
        return;
      }

      btnRegNext.disabled = true;
      btnRegNext.textContent = 'Checking...';
      try {
        const res = await fetch(`${API_BASE_URL}/auth/store/${encodeURIComponent(storeCode)}`);
        const data = await res.json();
        if (data && data.exists) {
          showRegisterError(`Store code '${storeCode}' is already registered. Please choose another.`, regStoreCode);
          return;
        }
      } catch (_) {
        // Proceed if health/network check unavailable; backend will still enforce uniqueness
      } finally {
        btnRegNext.disabled = false;
        btnRegNext.textContent = 'Next →';
      }

      goToRegStep(2);
    });
  }

  if (btnRegBack) {
    btnRegBack.addEventListener('click', () => goToRegStep(1));
  }

  btnOpenRegister.addEventListener('click', () => {
    clearRegisterErrors();
    resetRegisterBtn();
    goToRegStep(1);
    modalRegister.classList.add('active');
    regStoreName.focus();
  });

  const closeModal = () => {
    modalRegister.classList.remove('active');
    clearRegisterErrors();
    resetRegisterBtn();
    goToRegStep(1);
  };
  btnCloseRegister.addEventListener('click', closeModal);
  btnCancelRegister.addEventListener('click', closeModal);

  // Close modals on clicking outside modal card (backdrop click)
  modalRegister.addEventListener('click', (e) => {
    if (e.target === modalRegister) closeModal();
  });
  if (modalSuccess) {
    modalSuccess.addEventListener('click', (e) => {
      if (e.target === modalSuccess) modalSuccess.classList.remove('active');
    });
  }
  if (btnCloseSuccess) {
    btnCloseSuccess.addEventListener('click', () => {
      modalSuccess.classList.remove('active');
    });
  }
  if (btnProceedDashboard) {
    btnProceedDashboard.addEventListener('click', () => {
      window.location.href = 'dashboard.html';
    });
  }

  function showRegisterError(msg, targetInput = null) {
    registerErrorBox.textContent = msg;
    registerErrorBox.style.display = 'block';
    if (regBottomErrorBox) {
      regBottomErrorBox.style.display = 'none';
    }
    if (targetInput) {
      targetInput.classList.add('has-error');
      targetInput.focus();
    }
    const modalCard = modalRegister.querySelector('.modal-card');
    if (modalCard) {
      modalCard.classList.remove('modal-shake');
      void modalCard.offsetWidth;
      modalCard.classList.add('modal-shake');
      setTimeout(() => modalCard.classList.remove('modal-shake'), 450);
    }
    if (regModalBody) {
      regModalBody.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }

  function clearRegisterErrors() {
    registerErrorBox.style.display = 'none';
    if (regBottomErrorBox) regBottomErrorBox.style.display = 'none';
    modalRegister.querySelectorAll('.form-control').forEach(el => el.classList.remove('has-error'));
  }

  modalRegister.querySelectorAll('.form-control').forEach((el) => {
    el.addEventListener('input', () => {
      el.classList.remove('has-error');
      if (registerErrorBox.style.display === 'block') {
        registerErrorBox.style.display = 'none';
      }
    });
  });

  // Auto-slug generation for store code from store name
  regStoreName.addEventListener('input', () => {
    if (!regStoreCode.dataset.userEdited || !regStoreCode.value.trim()) {
      const slug = regStoreName.value
        .toLowerCase()
        .trim()
        .replace(/[^a-z0-9]/g, '');
      regStoreCode.value = slug;
      triggerStoreCodeCheck(slug);
    }
  });

  let storeCodeCheckTimer = null;
  function triggerStoreCodeCheck(code) {
    clearTimeout(storeCodeCheckTimer);
    if (!code || code.length < 2) {
      regStoreCodeFeedback.style.display = 'none';
      regStoreCode.classList.remove('has-error', 'has-success');
      return;
    }

    regStoreCodeFeedback.style.display = 'inline-flex';
    regStoreCodeFeedback.className = 'live-feedback-pill is-checking';
    regStoreCodeFeedback.textContent = 'Checking availability...';

    storeCodeCheckTimer = setTimeout(async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/auth/store/${encodeURIComponent(code)}`);
        const data = await res.json();
        if (data && data.exists) {
          regStoreCodeFeedback.className = 'live-feedback-pill is-taken';
          regStoreCodeFeedback.textContent = `❌ Store code '${code}' is already registered`;
          regStoreCode.classList.add('has-error');
          regStoreCode.classList.remove('has-success');
        } else {
          regStoreCodeFeedback.className = 'live-feedback-pill is-available';
          regStoreCodeFeedback.textContent = `✓ Store code '${code}' is available!`;
          regStoreCode.classList.add('has-success');
          regStoreCode.classList.remove('has-error');
        }
      } catch {
        regStoreCodeFeedback.style.display = 'none';
      }
    }, 280);
  }

  regStoreCode.addEventListener('input', () => {
    regStoreCode.dataset.userEdited = 'true';
    const cleanCode = regStoreCode.value.toLowerCase().replace(/[^a-z0-9_-]/g, '');
    regStoreCode.value = cleanCode;
    triggerStoreCodeCheck(cleanCode);
  });

  // Password strength & confirmation check
  const updatePasswordStrength = () => {
    if (!regPasswordStrength) return;
    const p = regPassword.value;
    if (!p) {
      regPasswordStrength.style.display = 'none';
      return;
    }
    regPasswordStrength.style.display = 'block';
    let score = 0;
    if (p.length >= 6) score++;
    if (p.length >= 10) score++;
    if (/[A-Z]/.test(p) && /[0-9]/.test(p)) score++;
    if (/[^A-Za-z0-9]/.test(p)) score++;

    regPasswordStrengthFill.className = 'password-strength-fill';
    if (score <= 1) {
      regPasswordStrengthFill.classList.add('weak');
      regPasswordStrengthLabel.textContent = 'Weak password';
    } else if (score <= 2) {
      regPasswordStrengthFill.classList.add('medium');
      regPasswordStrengthLabel.textContent = 'Medium strength';
    } else {
      regPasswordStrengthFill.classList.add('strong');
      regPasswordStrengthLabel.textContent = 'Strong password';
    }
  };

  const checkPasswordMatch = () => {
    updatePasswordStrength();
    const p1 = regPassword.value;
    const p2 = regPasswordConfirm.value;
    if (!p2) {
      regPasswordFeedback.style.display = 'none';
      regPasswordConfirm.classList.remove('has-error', 'has-success');
      return;
    }
    regPasswordFeedback.style.display = 'inline-flex';
    if (p1 === p2) {
      regPasswordFeedback.className = 'live-feedback-pill is-available';
      regPasswordFeedback.textContent = '✓ Passwords match';
      regPasswordConfirm.classList.add('has-success');
      regPasswordConfirm.classList.remove('has-error');
    } else {
      regPasswordFeedback.className = 'live-feedback-pill is-taken';
      regPasswordFeedback.textContent = '⚠️ Passwords do not match';
      regPasswordConfirm.classList.add('has-error');
      regPasswordConfirm.classList.remove('has-success');
    }
  };
  regPassword.addEventListener('input', checkPasswordMatch);
  regPasswordConfirm.addEventListener('input', checkPasswordMatch);

  // Form submission handler
  formRegister.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (regStep1 && regStep1.style.display !== 'none' && btnRegNext) {
      btnRegNext.click();
      return;
    }
    clearRegisterErrors();

    const storeName = regStoreName.value.trim();
    const storeCode = regStoreCode.value.trim().toLowerCase();
    const ownerName = regOwnerName.value.trim();
    const city = regCity.value.trim();
    const ownerEmail = regOwnerEmail.value.trim().toLowerCase();
    const phone = regPhone.value.trim();
    const address = regAddress ? regAddress.value.trim() : '';
    const adminUsername = regAdminUsername ? regAdminUsername.value.trim() : '';
    const password = regPassword.value;
    const passwordConfirm = regPasswordConfirm.value;

    // Strict validation according to application logic
    if (!storeName) {
      showRegisterError('Please enter your Store Name.', regStoreName);
      return;
    }
    if (!storeCode || storeCode.length < 2) {
      showRegisterError('Store Code must be at least 2 alphanumeric characters.', regStoreCode);
      return;
    }
    if (!ownerName) {
      showRegisterError('Please enter the Owner Full Name.', regOwnerName);
      return;
    }
    if (!city) {
      showRegisterError('Please enter the City.', regCity);
      return;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!ownerEmail || !emailRegex.test(ownerEmail)) {
      showRegisterError('Please enter a valid Owner Email address.', regOwnerEmail);
      return;
    }
    if (!password || password.length < 6) {
      showRegisterError('Admin Password must be at least 6 characters.', regPassword);
      return;
    }
    if (password !== passwordConfirm) {
      showRegisterError('Passwords do not match. Please re-enter confirm password.', regPasswordConfirm);
      return;
    }

    const payload = {
      storeName,
      storeCode,
      ownerName,
      ownerEmail,
      phone,
      city,
      address,
      password
    };
    if (adminUsername) {
      payload.adminUsername = adminUsername;
    }

    btnSubmitRegister.disabled = true;
    btnSubmitRegister.innerHTML = `<span>⏳</span> <span>Creating Store Workspace...</span>`;

    try {
      const res = await fetch(`${API_BASE_URL}/auth/register-store`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      const body = await res.json();
      if (!res.ok) {
        let msg = body.message;
        if (body.data && typeof body.data === 'object') {
          msg = Object.values(body.data).join(' • ');
        }
        throw new Error(msg || 'Failed to create store.');
      }

      const loginData = body.data;
      localStorage.setItem('grocery_token', loginData.token);
      localStorage.setItem('grocery_user', JSON.stringify({
        id: loginData.id,
        username: loginData.username,
        fullName: loginData.fullName,
        email: loginData.email,
        role: loginData.role,
        storeId: loginData.storeId,
        storeCode: loginData.storeCode,
        storeName: loginData.storeName
      }));
      localStorage.setItem('freshgrocery_last_store', loginData.storeCode);
      localStorage.setItem('freshgrocery_remembered_store', loginData.storeCode);

      // Hide register modal & reset submit button
      modalRegister.classList.remove('active');
      resetRegisterBtn();

      // Populate & show credentials confirmation modal
      document.getElementById('success-store-name').textContent = loginData.storeName;
      document.getElementById('success-store-code').textContent = loginData.storeCode;
      document.getElementById('success-admin-username').textContent = loginData.username;
      document.getElementById('success-owner-email').textContent = loginData.email;
      modalSuccess.classList.add('active');

    } catch (err) {
      btnSubmitRegister.disabled = false;
      btnSubmitRegister.innerHTML = `<span>🚀</span> <span>Create Store & Launch</span>`;
      showRegisterError(err.message || 'Registration failed. Please check the inputs.');
    }
  });

  // 10. Live Backend & MongoDB Heartbeat Ping
  async function checkServerHealth() {
    const statusIndicator = document.getElementById('live-system-status');
    if (!statusIndicator) return;
    try {
      const startTime = performance.now();
      const res = await fetch(`${API_BASE_URL}/auth/store/freshgrocery`);
      const latency = Math.round(performance.now() - startTime);

      if (res.ok) {
        statusIndicator.innerHTML = `
          <div class="status-dot-pulse"></div>
          <span>Cloud Online • Multi-Tenant Active • ${latency}ms</span>
        `;
      }
    } catch {
      statusIndicator.innerHTML = `
        <div class="status-dot-pulse" style="background: #ef4444;"></div>
        <span>Offline / Starting Up (:8080)</span>
      `;
    }
  }
  checkServerHealth();

  // Copy helpers for Success Modal
  const setupCopyChip = (btn, sourceEl) => {
    if (!btn || !sourceEl) return;
    btn.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(sourceEl.textContent.trim());
        btn.textContent = 'Copied!';
        setTimeout(() => { btn.textContent = 'Copy'; }, 1500);
      } catch (_) {}
    });
  };
  setupCopyChip(btnCopyStoreCode, document.getElementById('success-store-code'));
  setupCopyChip(btnCopyUsername, document.getElementById('success-admin-username'));

  // Helper Functions
  function setLoading(isLoading) {
    submitBtn.disabled = isLoading;
    submitBtn.classList.toggle('is-loading', isLoading);
    if (isLoading) {
      submitBtnText.textContent = 'Authenticating securely...';
    } else {
      submitBtnText.textContent = (currentPortalMode === 'STAFF') ? 'Sign In to Terminal' : 'Access Store Dashboard';
    }
  }

  function showError(msg) {
    errorBox.textContent = '';
    const iconSpan = document.createElement('span');
    iconSpan.textContent = '⚠️';
    const msgSpan = document.createElement('span');
    msgSpan.textContent = msg;
    errorBox.append(iconSpan, ' ', msgSpan);
    errorBox.style.display = 'flex';
  }

  function hideError() {
    errorBox.style.display = 'none';
  }

  function triggerShake() {
    if (!authCard) return;
    authCard.classList.remove('shake-card');
    void authCard.offsetWidth; // Force reflow
    authCard.classList.add('shake-card');
    setTimeout(() => authCard.classList.remove('shake-card'), 500);
  }
});
