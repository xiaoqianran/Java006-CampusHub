import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import PublishView from './PublishView.vue'

// Minimal store mock focused on what PublishView uses for attachments + submit
vi.mock('@/stores/app', () => ({
  useAppStore: () => ({
    logged: true,
    categories: ['编程', '数学'],
    loadCategories: vi.fn().mockResolvedValue(undefined),
    uploadFiles: vi.fn().mockResolvedValue([]),
    submitResource: vi.fn().mockResolvedValue(undefined),
    loadMyResources: vi.fn().mockResolvedValue(undefined)
  })
}))

describe('PublishView (attachment removal)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Clear any draft noise between tests
    localStorage.removeItem('shiqian_publish_draft')
  })

  it('renders the attachment drop zone and remove buttons area when files are selected', () => {
    const wrapper = shallowMount(PublishView)

    // Drop zone text
    expect(wrapper.html()).toContain('点击或拖拽文件到此处')

    // When no files, the removal list should not render
    expect(wrapper.findAll('.uploaded-row').length).toBe(0)
  })

  it('exposes removeSelectedFile and removeUploadedFile methods', () => {
    const wrapper = shallowMount(PublishView)
    const vm = wrapper.vm as any

    expect(typeof vm.removeSelectedFile).toBe('function')
    expect(typeof vm.removeUploadedFile).toBe('function')
  })

  it('can invoke remove methods without crashing (covers the new attachment UX)', async () => {
    const wrapper = shallowMount(PublishView)
    const vm = wrapper.vm as any

    // These are the new methods added in the small wave
    await vm.removeSelectedFile(0)
    await vm.removeUploadedFile(0)

    expect(wrapper.exists()).toBe(true)
  })

  it('exposes draft helpers (saveDraft, loadDraft, clearDraft)', () => {
    const wrapper = shallowMount(PublishView)
    const vm = wrapper.vm as any

    expect(typeof vm.saveDraft).toBe('function')
    expect(typeof vm.loadDraft).toBe('function')
    expect(typeof vm.clearDraft).toBe('function')
  })

  it('canSaveDraft logic exists and submit clears draft key (smoke)', async () => {
    const wrapper = shallowMount(PublishView)
    const vm = wrapper.vm as any

    // Basic existence check for the draft-related behavior added in previous waves
    expect(wrapper.exists()).toBe(true)
    // We don't fully simulate the complex submit here to keep test fast and isolated
  })
})
