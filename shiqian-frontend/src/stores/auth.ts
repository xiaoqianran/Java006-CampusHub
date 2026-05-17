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
    isAuthenticated: (state) => Boolean(state.token?.accessToken)
  },
  actions: {
    setSession(token: AuthToken, user: LoginUser) {
      this.token = token;
      this.user = user;
      setAccessToken(token.accessToken);
    },
    clearSession() {
      this.token = null;
      this.user = null;
      clearAccessToken();
    }
  }
});
