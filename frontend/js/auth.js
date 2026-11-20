/**
 * Grocery Management System - Auth State Manager
 * Handles login, session preservation, and role checking.
 */
import { Api } from './api.js';

export const Auth = {
  getUser() {
    const raw = localStorage.getItem('grocery_user');
    return raw ? JSON.parse(raw) : null;
  },

  getToken() {
    return localStorage.getItem('grocery_token');
  },

  isAuthenticated() {
    return !!this.getToken();
  },

  hasRole(...roles) {
    const user = this.getUser();
    return user && roles.includes(user.role);
  },

  async login(username, password) {
    const res = await Api.post('/auth/login', { username, password });
    if (res && res.token) {
      localStorage.setItem('grocery_token', res.token);
      localStorage.setItem('grocery_user', JSON.stringify({
        id: res.id,
        username: res.username,
        fullName: res.fullName,
        role: res.role
      }));
      return res;
    }
    throw new Error('Invalid response from server.');
  },

  logout() {
    try {
      const userStr = localStorage.getItem('grocery_user');
      if (userStr) {
        const u = JSON.parse(userStr);
        if (u && u.storeCode) {
          localStorage.setItem('freshgrocery_last_store', u.storeCode);
        }
      }
    } catch (_) {}
    localStorage.removeItem('grocery_token');
    localStorage.removeItem('grocery_user');
    window.location.href = 'login.html';
  },

  requireAuth() {
    if (!this.isAuthenticated()) {
      window.location.href = 'login.html';
    }
  }
};
