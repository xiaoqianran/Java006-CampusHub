import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import UserAdminView from './UserAdminView.vue'

vi.mock('@/stores/app', () => ({
  useAppStore: () => ({
    users: [
      { id: 1, username: 'student01', nickname: '学生1', role: 'USER', status: '正常' },
      { id: 2, username: 'admin', nickname: '管理员', role: 'ADMIN', status: '正常' }
    ],
    loadUsers: vi.fn().mockResolvedValue(undefined),
    updateUserStatus: vi.fn().mockResolvedValue(undefined),
    updateUserRole: vi.fn().mockResolvedValue(undefined)
  })
}))

describe('UserAdminView (status + role)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('mounts successfully (admin user management view)', () => {
    const wrapper = shallowMount(UserAdminView, {
      global: {
        stubs: {
          AdminLayout: { template: '<div><slot /></div>' }
        }
      }
    })

    expect(wrapper.exists()).toBe(true)
  })
})
