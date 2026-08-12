import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual('@/api/client')
  return {
    ...actual,
    request: vi.fn().mockResolvedValue(undefined),
    jsonBody: (actual as any).jsonBody
  }
})

import { useAuthStore } from './auth'
import { useResourceStore } from './resource'
import { request } from '@/api/client'

describe('auth store - critical methods', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked(request).mockResolvedValue(undefined as any)
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.currentUser = null as any
  })

  it('updateProfile calls the correct API and updates local currentUser', async () => {
    const auth = useAuthStore()
    auth.currentUser = { userId: 1, nickname: 'old', email: '', phone: '', avatar: '' } as any

    await auth.updateProfile({ nickname: '新昵称', email: 'a@b.com' })

    expect(auth.currentUser?.nickname).toBe('新昵称')
  })

  it('logout calls server revoke endpoint then clears local session', async () => {
    localStorage.setItem('shiqian_access_token', 'access-x')
    localStorage.setItem('shiqian_refresh_token', 'refresh-x')
    const auth = useAuthStore()
    const resource = useResourceStore()
    auth.logged = true
    auth.currentUser = { userId: 1, username: 'u', nickname: 'n', role: 'USER' } as any
    resource.favoriteIds = [9]
    resource.myResourceIds = [3]

    await auth.logout()

    expect(vi.mocked(request)).toHaveBeenCalledWith('/api/user/logout', { method: 'POST' })
    expect(auth.logged).toBe(false)
    expect(auth.currentUser).toBeNull()
    expect(resource.favoriteIds).toEqual([])
    expect(localStorage.getItem('shiqian_access_token')).toBeNull()
  })

  it('changePassword hits password API and clears session tokens', async () => {
    localStorage.setItem('shiqian_access_token', 'access-x')
    localStorage.setItem('shiqian_refresh_token', 'refresh-x')
    const auth = useAuthStore()
    auth.logged = true
    auth.currentUser = { userId: 1, username: 'u', nickname: 'n', role: 'USER' } as any

    await auth.changePassword({ oldPassword: 'old-pass', newPassword: 'new-pass-1' })

    expect(vi.mocked(request)).toHaveBeenCalledWith('/api/user/me/password', {
      method: 'PUT',
      body: expect.stringContaining('old-pass')
    })
    expect(auth.logged).toBe(false)
    expect(auth.currentUser).toBeNull()
    expect(localStorage.getItem('shiqian_access_token')).toBeNull()
    expect(localStorage.getItem('shiqian_refresh_token')).toBeNull()
  })
})
