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

import { useAdminStore } from './admin'
import { request } from '@/api/client'

describe('admin store - critical methods', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked(request).mockResolvedValue(undefined as any)
    setActivePinia(createPinia())
  })

  it('recordAdminLog is exposed and callable (used by admin views)', async () => {
    const admin = useAdminStore()
    expect(typeof admin.recordAdminLog).toBe('function')

    await admin.recordAdminLog('TEST_ACTION', 123, 'detail')
  })
})
