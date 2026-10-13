/**
 * Grocery Management System - Layout & Navigation Renderer
 * Renders consistent sidebar navigation, header profile, and toasts.
 */
import { Auth } from './auth.js';

export const Layout = {
  init({ activePage = 'dashboard', title = 'Dashboard' } = {}) {
    Auth.requireAuth();
    const user = Auth.getUser() || { fullName: 'Staff User', role: 'CASHIER' };

    // Set Document Title
    document.title = `${title} | Grocery Management System`;

    // Render Navigation
    this.renderSidebar(activePage, user);
    this.renderTopbar(title, user);
    this.setupToasts();
  },

  renderSidebar(activePage, user) {
    const sidebar = document.getElementById('sidebar-container');
    if (!sidebar) return;

    const navItems = [
      { id: 'dashboard', label: 'Dashboard', icon: '📊', href: 'dashboard.html', roles: ['ADMIN', 'STORE_MANAGER', 'INVENTORY_STAFF'] },
      { id: 'pos', label: 'Point of Sale (POS)', icon: '🛒', href: 'pos.html', roles: ['ADMIN', 'STORE_MANAGER', 'CASHIER'] },
      { id: 'products', label: 'Products', icon: '📦', href: 'products.html', roles: ['ADMIN', 'STORE_MANAGER', 'INVENTORY_STAFF'] },
      { id: 'categories', label: 'Categories', icon: '🏷️', href: 'categories.html', roles: ['ADMIN', 'STORE_MANAGER'] },
      { id: 'inventory', label: 'Inventory & Batches', icon: '📋', href: 'inventory.html', roles: ['ADMIN', 'STORE_MANAGER', 'INVENTORY_STAFF'] },
      { id: 'purchases', label: 'Purchases', icon: '🚚', href: 'purchases.html', roles: ['ADMIN', 'STORE_MANAGER'] },
      { id: 'suppliers', label: 'Suppliers', icon: '🏢', href: 'suppliers.html', roles: ['ADMIN', 'STORE_MANAGER'] },
      { id: 'sales', label: 'Sales History', icon: '🧾', href: 'sales.html', roles: ['ADMIN', 'STORE_MANAGER', 'CASHIER'] },
      { id: 'returns', label: 'Returns & Refunds', icon: '🔄', href: 'returns.html', roles: ['ADMIN', 'STORE_MANAGER', 'CASHIER'] },
      { id: 'customers', label: 'Customers', icon: '👥', href: 'customers.html', roles: ['ADMIN', 'STORE_MANAGER', 'CASHIER'] },
      { id: 'expenses', label: 'Operating Expenses', icon: '💰', href: 'expenses.html', roles: ['ADMIN', 'STORE_MANAGER'] },
      { id: 'reports', label: 'Reports & Analytics', icon: '📈', href: 'reports.html', roles: ['ADMIN', 'STORE_MANAGER'] },
      { id: 'notifications', label: 'Alerts', icon: '🔔', href: 'notifications.html', roles: ['ADMIN', 'STORE_MANAGER', 'INVENTORY_STAFF'] },
      { id: 'users', label: 'User Management', icon: '🔐', href: 'users.html', roles: ['ADMIN'] },
      { id: 'settings', label: 'Store Settings', icon: '⚙️', href: 'settings.html', roles: ['ADMIN'] },
    ];

    const allowedItems = navItems.filter(item => item.roles.includes(user.role));

    sidebar.innerHTML = `
      <aside class="sidebar" id="sidebar">
        <div class="sidebar-header">
          <div class="sidebar-logo-icon">🌿</div>
          <div class="sidebar-title">FreshGrocery</div>
        </div>
        <nav class="sidebar-nav">
          <div class="nav-section-title">Menu</div>
          ${allowedItems.map(item => `
            <a href="${item.href}" class="nav-item ${item.id === activePage ? 'active' : ''}">
              <span class="nav-icon">${item.icon}</span>
              <span>${item.label}</span>
            </a>
          `).join('')}
        </nav>
        <div class="sidebar-footer">
          <div class="user-snippet">
            <div class="user-avatar">${user.fullName ? user.fullName[0].toUpperCase() : 'U'}</div>
            <div class="user-meta">
              <div class="user-meta-name">${user.fullName || 'User'}</div>
              <div class="user-meta-role">${user.role}</div>
            </div>
          </div>
        </div>
      </aside>
    `;
  },

  renderTopbar(title, user) {
    const topbar = document.getElementById('topbar-container');
    if (!topbar) return;

    topbar.innerHTML = `
      <header class="topbar">
        <div class="topbar-left">
          <h1 class="page-title">${title}</h1>
        </div>
        <div class="topbar-right">
          <a href="notifications.html" class="btn-icon" title="Notifications">
            🔔
            <span class="notification-count" id="unread-alert-count">0</span>
          </a>
          <button class="btn btn-secondary" id="logout-btn">
            🚪 Logout
          </button>
        </div>
      </header>
    `;

    document.getElementById('logout-btn')?.addEventListener('click', () => {
      Auth.logout();
    });
  },

  setupToasts() {
    if (!document.getElementById('toast-container')) {
      const container = document.createElement('div');
      container.id = 'toast-container';
      document.body.appendChild(container);
    }
  },

  showToast(message, type = 'info') {
    this.setupToasts();
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    container.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateX(100%)';
      toast.style.transition = 'all 0.25s ease';
      setTimeout(() => toast.remove(), 250);
    }, 3500);
  }
};
