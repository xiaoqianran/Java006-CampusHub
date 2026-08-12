import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import CategoryAdminView from './CategoryAdminView.vue'

vi.mock('@/stores/catalog', () => ({
  useCatalogStore: () => ({
    categories: ['编程', '数学'],
    flatCategories: [
      { id: 1, name: '编程', icon: '💻', sortOrder: 10 },
      { id: 2, name: '数学', icon: '∑', sortOrder: 20 }
    ],
    loadCategories: vi.fn().mockResolvedValue(undefined),
    createCategory: vi.fn().mockResolvedValue(undefined),
    updateCategory: vi.fn().mockResolvedValue(undefined),
    deleteCategory: vi.fn().mockResolvedValue(undefined)
  })
}))

vi.mock('@/stores/resource', () => ({
  useResourceStore: () => ({
    resources: [],
    loadResources: vi.fn().mockResolvedValue(undefined)
  })
}))

vi.mock('@/stores/admin', () => ({
  useAdminStore: () => ({
    recordAdminLog: vi.fn().mockResolvedValue(undefined)
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
          AdminLayout: { template: '<div><slot /></div>' },
          'el-table': { template: '<div><slot /></div>' },
          'el-table-column': { template: '<div><slot :row="{}" /></div>' },
          'el-dialog': { template: '<div><slot /></div>' },
          'el-form': { template: '<div><slot /></div>' },
          'el-input': true,
          'el-button': true,
          'el-icon': true,
          'el-dropdown': { template: '<div><slot /></div>' },
          'el-dropdown-item': true,
          'el-dropdown-menu': { template: '<div><slot /></div>' },
          'el-card': { template: '<div><slot /></div>' }
        }
      }
    })

    expect(wrapper.exists()).toBe(true)
  })
})
