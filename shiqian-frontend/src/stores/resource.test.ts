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

describe('resource store - critical methods', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked(request).mockResolvedValue(undefined as any)
    setActivePinia(createPinia())
    useAuthStore().currentUser = null as any
  })

  it('deduplicates concurrent home loads and reuses the short-lived cache', async () => {
    vi.mocked(request).mockImplementation(async path => {
      if (path === '/api/resource') {
        return { records: [], total: 0, size: 100, current: 1, pages: 0 } as any
      }
      return undefined as any
    })
    const resource = useResourceStore()

    await Promise.all([resource.loadHomeData(), resource.loadHomeData()])
    await resource.loadHomeData()

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
    const auth = useAuthStore()
    const resource = useResourceStore()
    auth.logged = true

    await resource.loadResourceDetail(7, { includeFavorite: false })

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
    const resource = useResourceStore()
    resource.keyword = 'Java'

    await resource.searchResources({ page: 2, size: 24, scene: 'GALLERY' })

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
    expect(resource.searchResultTotal).toBe(2)
  })
})
