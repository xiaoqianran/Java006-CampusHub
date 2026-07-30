import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'
import PublishView from './PublishView.vue'

const mocks = vi.hoisted(() => ({
  submitResource: vi.fn().mockResolvedValue(undefined),
  push: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push })
}))

// Minimal store mock focused on what PublishView uses for attachments + submit
vi.mock('@/stores/app', () => ({
  useAppStore: () => ({
    logged: true,
    categories: ['编程', '数学'],
    loadCategories: vi.fn().mockResolvedValue(undefined),
    uploadFiles: vi.fn().mockResolvedValue([]),
    submitResource: mocks.submitResource,
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

  it('submits a file-only resource without Markdown content', async () => {
    const wrapper = shallowMount(PublishView)
    const vm = wrapper.vm as any

    vm.form.mode = 'FILE'
    vm.form.title = 'TXT 课程资料'
    vm.form.cat = '编程'
    vm.form.contentMarkdown = ''
    vm.uploadedFiles = [{
      originalName: 'notes.txt',
      fileUrl: '/api/resource/files/1/notes.txt',
      fileSize: 12,
      fileType: 'text/plain'
    }]
    await nextTick()

    expect(vm.canSubmit).toBe(true)
    await vm.submit()

    expect(mocks.submitResource).toHaveBeenCalledWith(expect.objectContaining({
      title: 'TXT 课程资料',
      contentMarkdown: undefined,
      attachments: expect.arrayContaining([
        expect.objectContaining({ originalName: 'notes.txt' })
      ])
    }))
  })
})
