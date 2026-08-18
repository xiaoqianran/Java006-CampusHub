import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearTokens,
  getAccessToken,
  request,
  setAuthFailureHandler,
  setTokens
} from './client'

describe('api client auth handling', () => {
  beforeEach(() => {
    localStorage.clear()
    clearTokens()
    setAuthFailureHandler(null)
    vi.restoreAllMocks()
  })

  afterEach(() => {
    clearTokens()
    localStorage.clear()
    setAuthFailureHandler(null)
  })

  it('refreshes on 401 then retries once without persisting tokens to Web Storage', async () => {
    setTokens('old-access')
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: false,
        status: 401,
        json: async () => ({ code: 401, message: 'expired', data: null })
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => ({
          code: 200,
          message: 'ok',
          data: { accessToken: 'new-access' }
        })
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => ({ code: 200, message: 'ok', data: { id: 1 } })
      })
    vi.stubGlobal('fetch', fetchMock)

    const data = await request<{ id: number }>('/api/resource/mine')
    expect(data).toEqual({ id: 1 })
    expect(getAccessToken()).toBe('new-access')
    expect(localStorage.getItem('shiqian_access_token')).toBeNull()
    expect(localStorage.getItem('shiqian_refresh_token')).toBeNull()
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[1]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      cache: 'no-store'
    })
  })

  it('clears session when refresh fails after auth error', async () => {
    setTokens('old-access')
    const onFail = vi.fn()
    setAuthFailureHandler(onFail)
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: false,
        status: 401,
        json: async () => ({
          code: 401,
          message: 'expired',
          data: { reason: 'token_expired' }
        })
      })
      .mockResolvedValueOnce({
        ok: false,
        status: 401,
        json: async () => ({ code: 401, message: 'refresh failed', data: null })
      })
    vi.stubGlobal('fetch', fetchMock)

    await expect(request('/api/resource/mine')).rejects.toThrow('登录已过期')
    expect(onFail).toHaveBeenCalled()
    expect(getAccessToken()).toBe('')
    expect(localStorage.getItem('shiqian_access_token')).toBeNull()
    expect(localStorage.getItem('shiqian_refresh_token')).toBeNull()
  })

  it('treats 403 with business code 401 as auth error', async () => {
    setTokens('old-access')
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: false,
        status: 403,
        json: async () => ({ code: 401, message: '未登录', data: null })
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => ({
          code: 200,
          message: 'ok',
          data: { accessToken: 'a2' }
        })
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => ({ code: 200, message: 'ok', data: 'ok' })
      })
    vi.stubGlobal('fetch', fetchMock)

    await expect(request('/api/resource/favorites')).resolves.toBe('ok')
    expect(getAccessToken()).toBe('a2')
    expect(localStorage.getItem('shiqian_access_token')).toBeNull()
  })
})
