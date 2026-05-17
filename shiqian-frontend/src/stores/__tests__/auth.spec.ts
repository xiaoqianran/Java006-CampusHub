import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../auth'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => { store[key] = value },
    removeItem: (key: string) => { delete store[key] },
    clear: () => { store = {} }
  }
})()

Object.defineProperty(window, 'localStorage', { value: localStorageMock })

// Mock http.ts 中的函数
vi.mock('../../api/http', () => ({
  setAccessToken: vi.fn(),
  clearAccessToken: vi.fn()
}))

describe('Auth Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorageMock.clear()
  })

  it('setSession 后 isAuthenticated 应为 true', () => {
    const store = useAuthStore()
    store.setSession(
      { accessToken: 'fake-token', refreshToken: 'fake-refresh' },
      { userId: 1, username: 'test', role: 'USER' }
    )

    expect(store.isAuthenticated).toBe(true)
    expect(store.user?.username).toBe('test')
  })

  it('clearSession 后状态应被清空', () => {
    const store = useAuthStore()
    store.setSession(
      { accessToken: 'fake-token', refreshToken: 'fake-refresh' },
      { userId: 1, username: 'test', role: 'USER' }
    )
    store.clearSession()

    expect(store.isAuthenticated).toBe(false)
    expect(store.user).toBe(null)
    expect(localStorage.getItem('shiqian_user')).toBeNull()
  })

  it('hydrateFromStorage 应能恢复用户信息', () => {
    localStorage.setItem('shiqian_user', JSON.stringify({ userId: 99, username: 'restored', role: 'ADMIN' }))

    const store = useAuthStore()
    store.hydrateFromStorage()

    expect(store.user?.userId).toBe(99)
    expect(store.isAdmin).toBe(true)
  })
})