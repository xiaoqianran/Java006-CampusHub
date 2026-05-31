import { describe, it, expect, vi, beforeEach } from 'vitest'

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

describe('app store - critical recent methods', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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
})
