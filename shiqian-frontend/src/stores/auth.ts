import { defineStore } from 'pinia';
import { clearAccessToken, setAccessToken } from '../api/http';
import type { AuthToken, LoginUser } from '../types/api';

interface AuthState {
  token: AuthToken | null;
  user: LoginUser | null;
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: null,
    user: null
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token?.accessToken),
    isAdmin: (state) => state.user?.role === 'ADMIN',
    currentUserId: (state) => state.user?.userId
  },
  actions: {
    setSession(token: AuthToken, user: LoginUser) {
      this.token = token;
      this.user = user;
      setAccessToken(token.accessToken);
      // 持久化用户基础信息（刷新后仍可用）
      localStorage.setItem('shiqian_user', JSON.stringify(user));
    },
    clearSession() {
      this.token = null;
      this.user = null;
      clearAccessToken();
      localStorage.removeItem('shiqian_user');
    },
    // 应用启动时从 localStorage 恢复用户（token 由 http 拦截器自己管）
    hydrateFromStorage() {
      const raw = localStorage.getItem('shiqian_user');
      if (raw && !this.user) {
        try {
          this.user = JSON.parse(raw);
        } catch {}
      }
    }
  }
});
