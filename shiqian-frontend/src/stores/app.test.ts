import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// We test the exported functions / behavior by mocking the internal request helper
vi.mock('@/api/client', async () => {
  const actual = await vi.importActual('@/api/client')
  return {
    ...actual,
    request: vi.fn().mockResolvedValue(undefined),
    jsonBody: (actual as any).jsonBody
  }
})

import { useAppStore } from './app'
import { request } from '@/api/client'

describe('app store - critical recent methods', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked(request).mockResolvedValue(undefined as any)
    // Proper Pinia context for store tests (required after store grew with theme/hotResources/etc)
    setActivePinia(createPinia())
    // Reset simple state between tests
    const store = useAppStore()
    store.currentUser = null as any
  })

  it('updateProfile calls the correct API and updates local currentUser', async () => {
    const store = useAppStore()
    store.currentUser = { userId: 1, nickname: 'old', email: '', phone: '', avatar: '' } as any

    await store.updateProfile({ nickname: '新昵称', email: 'a@b.com' })

    // Basic smoke: method exists and runs without throwing with our mock
    expect(store.currentUser?.nickname).toBe('新昵称')
  })

  it('recordAdminLog is exposed and callable (used by admin views)', async () => {
    const store = useAppStore()
    // The method should exist after the audit log wave
    expect(typeof (store as any).recordAdminLog).toBe('function')

    // Call it - should not throw
    await (store as any).recordAdminLog('TEST_ACTION', 123, 'detail')
  })

  it('deduplicates concurrent home loads and reuses the short-lived cache', async () => {
    vi.mocked(request).mockImplementation(async path => {
      if (path === '/api/resource') {
        return { records: [], total: 0, size: 100, current: 1, pages: 0 } as any
      }
      return undefined as any
    })
    const store = useAppStore()

    await Promise.all([store.loadHomeData(), store.loadHomeData()])
    await store.loadHomeData()

    expect(vi.mocked(request).mock.calls.filter(([path]) => path === '/api/category/tree')).toHaveLength(0)
    expect(vi.mocked(request).mock.calls.filter(([path]) => path === '/api/resource')).toHaveLength(1)
  })

  it('loads audit detail without requesting favorite state', async () => {
    vi.mocked(request).mockImplementation(async path => {
      if (path === '/api/resource/7') {
        return { id: 7, userId: 1, title: '待审核资源', status: 0, attachments: [] } as any
      }
      return false as any
    })
    const store = useAppStore()
    store.logged = true

    await store.loadResourceDetail(7, { includeFavorite: false })

    expect(vi.mocked(request)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(request)).toHaveBeenCalledWith('/api/resource/7')
  })

  it('does not fan out one detail request per search result', async () => {
    vi.mocked(request).mockResolvedValue({
      records: [
        { id: 1, userId: 1, title: 'Java', status: 1 },
        { id: 2, userId: 2, title: 'Java实验', status: 1 }
      ],
      total: 2,
      size: 100,
      current: 1,
      pages: 1
    } as any)
    const store = useAppStore()
    store.keyword = 'Java'

    await store.searchResources({ page: 2, size: 24, scene: 'GALLERY' })

    expect(vi.mocked(request)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(request).mock.calls[0][0]).toBe('/api/resource/search')
    expect(vi.mocked(request).mock.calls[0][1]).toMatchObject({
      query: {
        keyword: 'Java',
        page: 2,
        size: 24,
        scene: 'GALLERY'
      }
    })
    expect(store.searchResultTotal).toBe(2)
  })

  it('logout calls server revoke endpoint then clears local session', async () => {
    localStorage.setItem('shiqian_access_token', 'access-x')
    localStorage.setItem('shiqian_refresh_token', 'refresh-x')
    const store = useAppStore()
    store.logged = true
    store.currentUser = { userId: 1, username: 'u', nickname: 'n', role: 'USER' } as any
    store.favoriteIds = [9]
    store.myResourceIds = [3]

    await store.logout()

    expect(vi.mocked(request)).toHaveBeenCalledWith('/api/user/logout', { method: 'POST' })
    expect(store.logged).toBe(false)
    expect(store.currentUser).toBeNull()
    expect(store.favoriteIds).toEqual([])
    expect(localStorage.getItem('shiqian_access_token')).toBeNull()
  })

  it('changePassword hits password API and clears session tokens', async () => {
    localStorage.setItem('shiqian_access_token', 'access-x')
    localStorage.setItem('shiqian_refresh_token', 'refresh-x')
    const store = useAppStore()
    store.logged = true
    store.currentUser = { userId: 1, username: 'u', nickname: 'n', role: 'USER' } as any

    await store.changePassword({ oldPassword: 'old-pass', newPassword: 'new-pass-1' })

    expect(vi.mocked(request)).toHaveBeenCalledWith('/api/user/me/password', {
      method: 'PUT',
      body: expect.stringContaining('old-pass')
    })
    expect(store.logged).toBe(false)
    expect(store.currentUser).toBeNull()
    expect(localStorage.getItem('shiqian_access_token')).toBeNull()
    expect(localStorage.getItem('shiqian_refresh_token')).toBeNull()
  })
})
