import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { ref } from 'vue'
import AdminLogView from './AdminLogView.vue'

// Mock the admin store (provide adminLogs + loadAdminLogs) - must be before import of SUT
vi.mock('@/stores/admin', () => {
  const adminLogs = ref([] as any[])

  return {
    useAdminStore: () => ({
      adminLogs,
      loadAdminLogs: vi.fn().mockResolvedValue({ total: 0, records: [] })
    })
  }
})

describe('AdminLogView (small surface)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function mountWithStubs() {
    return shallowMount(AdminLogView, {
      global: {
        stubs: {
          // Provide a stub that renders its slot content so titles and body are visible
          AdminLayout: {
            template: '<div class="admin-layout-stub"><slot /></div>'
          },
          // Stub heavy Element components for speed + stability
          'el-select': true,
          'el-option': true,
          'el-button': true,
          'el-table': true,
          'el-table-column': true,
          'el-tag': true,
          'el-pagination': true
        }
      }
    })
  }

  it('mounts successfully (admin log view)', () => {
    const wrapper = mountWithStubs()
    expect(wrapper.exists()).toBe(true)
  })
})
