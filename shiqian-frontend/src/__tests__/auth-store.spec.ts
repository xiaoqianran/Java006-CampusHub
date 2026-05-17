import { setActivePinia, createPinia } from 'pinia';
import { beforeEach } from 'vitest';
import { useAuthStore } from '../stores/auth';

describe('auth store', () => {
  beforeEach(() => {
    window.localStorage.clear();
    setActivePinia(createPinia());
  });

  it('uses anonymous initial state', () => {
    const authStore = useAuthStore();

    expect(authStore.token).toBeNull();
    expect(authStore.user).toBeNull();
    expect(authStore.isAuthenticated).toBe(false);
  });

  it('stores and clears login session', () => {
    const authStore = useAuthStore();

    authStore.setSession(
      { accessToken: 'access-token', refreshToken: 'refresh-token' },
      { userId: 1, username: 'alice', role: 'USER' }
    );

    expect(authStore.isAuthenticated).toBe(true);
    expect(window.localStorage.getItem('shiqian_access_token')).toBe('access-token');

    authStore.clearSession();

    expect(authStore.isAuthenticated).toBe(false);
    expect(window.localStorage.getItem('shiqian_access_token')).toBeNull();
  });
});
