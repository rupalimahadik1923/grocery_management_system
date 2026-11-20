import { Auth } from './auth.js';

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

  // Register Modal Elements
  const modalRegister = document.getElementById('register-store-modal');
  const regModalBody = document.getElementById('register-modal-body');
  const btnOpenRegister = document.getElementById('btn-open-register');
  const btnCloseRegister = document.getElementById('btn-close-register');
  const btnCancelRegister = document.getElementById('btn-cancel-register');
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
      const res = await fetch(`http://localhost:8080/api/v1/auth/store/${code}`);
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

  // 6. Password Visibility Toggle
  if (togglePasswordBtn) {
    togglePasswordBtn.addEventListener('click', () => {
      const isPassword = passwordInput.type === 'password';
      passwordInput.type = isPassword ? 'text' : 'password';
      togglePasswordBtn.setAttribute('aria-label', isPassword ? 'Hide password' : 'Show password');
      togglePasswordBtn.innerHTML = isPassword
        ? `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line></svg>`
        : `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>`;
    });
  }

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

  // 8. Main Form Submission Handler
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideError();

    const username = usernameInput.value.trim();
    const password = passwordInput.value;
    const storeCode = (currentPortalMode === 'STAFF') ? storeCodeInput.value.trim().toLowerCase() : null;

    if (currentPortalMode === 'STAFF' && !storeCode) {
      showError('Please enter your Store Code.');
      return;
    }
    if (!username || !password) {
      showError('Please enter both username and password.');
      return;
    }

    setLoading(true);

    try {
      const payload = { username, password };
      if (storeCode) payload.storeCode = storeCode;

      const res = await fetch('http://localhost:8080/api/v1/auth/login', {
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

  // 9. Register New Store Modal Logic
  btnOpenRegister.addEventListener('click', () => {
    clearRegisterErrors();
    modalRegister.classList.add('active');
    regStoreName.focus();
  });

  const closeModal = () => {
    modalRegister.classList.remove('active');
    clearRegisterErrors();
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
      regBottomErrorBox.textContent = msg;
      regBottomErrorBox.style.display = 'block';
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
        const res = await fetch(`http://localhost:8080/api/v1/auth/store/${encodeURIComponent(code)}`);
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

  // Password confirmation check
  const checkPasswordMatch = () => {
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
      const res = await fetch('http://localhost:8080/api/v1/auth/register-store', {
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

      // Hide register modal
      modalRegister.classList.remove('active');

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
      const res = await fetch('http://localhost:8080/api/v1/auth/store/freshgrocery');
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

  // Helper Functions
  function setLoading(isLoading) {
    submitBtn.disabled = isLoading;
    if (isLoading) {
      submitBtn.innerHTML = `<div class="spinner"></div> <span>Authenticating securely...</span>`;
    } else {
      submitBtn.innerHTML = `<span>${submitBtnText.textContent}</span> <span>➔</span>`;
    }
  }

  function showError(msg) {
    errorBox.innerHTML = `<span>⚠️</span> <span>${msg}</span>`;
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
