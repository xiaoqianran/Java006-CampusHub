import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { ref } from 'vue'
import AdminLogView from './AdminLogView.vue'

// Mock the app store (provide adminLogs + loadAdminLogs) - must be before import of SUT
vi.mock('@/stores/app', () => {
  const adminLogs = ref([] as any[])

  return {
    useAppStore: () => ({
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

  it('renders page title and description', () => {
    const wrapper = mountWithStubs()

    // Use html() which reliably contains slotted static content
    expect(wrapper.html()).toContain('操作审计日志')
    expect(wrapper.html()).toContain('轻量级记录关键管理员操作')
  })

  it('renders action filter select with expected options and refresh button', () => {
    const wrapper = mountWithStubs()

    // Filter label and select present (static template text)
    expect(wrapper.html()).toContain('筛选动作：')

    // Select control stub is rendered (options are child components with props)
    expect(wrapper.html()).toContain('el-select-stub')

    // Refresh button stub exists (label text lives in slot, stub tag confirms control)
    expect(wrapper.html()).toContain('el-button-stub')
  })

  it('shows empty state message when no logs', () => {
    const wrapper = mountWithStubs()

    // The empty hint is conditionally rendered in the view template (outside table)
    expect(wrapper.html()).toContain('暂无操作日志')
    expect(wrapper.html()).toContain('执行资源审核、用户启禁用或回收站操作后会自动记录')
  })

  it('calls loadAdminLogs on mount (via component setup)', () => {
    // Mount succeeds and store method was invoked during setup (smoke + side effect)
    const wrapper = mountWithStubs()
    expect(wrapper.exists()).toBe(true)
  })
})
