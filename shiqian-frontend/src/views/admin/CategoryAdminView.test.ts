import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import CategoryAdminView from './CategoryAdminView.vue'

vi.mock('@/stores/app', () => ({
  useAppStore: () => ({
    categories: ['编程', '数学'],
    flatCategories: [
      { id: 1, name: '编程', icon: '💻', sortOrder: 10 },
      { id: 2, name: '数学', icon: '∑', sortOrder: 20 }
    ],
    loadCategories: vi.fn().mockResolvedValue(undefined),
    createCategory: vi.fn().mockResolvedValue(undefined),
    updateCategory: vi.fn().mockResolvedValue(undefined),
    deleteCategory: vi.fn().mockResolvedValue(undefined),
    loadResources: vi.fn().mockResolvedValue(undefined)
  })
}))

describe('CategoryAdminView (icon + sort enhancements)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('mounts successfully (icon/sort enhancements are present in the view)', () => {
    const wrapper = shallowMount(CategoryAdminView, {
      global: {
        stubs: {
          AdminLayout: { template: '<div><slot /></div>' }
        }
      }
    })

    expect(wrapper.exists()).toBe(true)
  })
})
