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

  it('shows attachment upload in every channel', async () => {
    const wrapper = shallowMount(PublishView)
    expect(wrapper.html()).toContain('点击或拖拽文件到此处')
    expect(wrapper.html()).toContain('博客帖')
    expect(wrapper.html()).toContain('图片帖')
    expect(wrapper.html()).toContain('资料分享帖')
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

  it('uses the live Markdown editor with simultaneous preview', () => {
    const wrapper = shallowMount(PublishView)

    expect(wrapper.findComponent({ name: 'MarkdownLiveEditor' }).exists()).toBe(true)
  })

  it('allows an attachment-only gallery post without category or Markdown', async () => {
    const wrapper = shallowMount(PublishView)
    const vm = wrapper.vm as any

    vm.form.scene = 'GALLERY'
    vm.form.title = '校园摄影'
    vm.form.cat = ''
    vm.form.contentMarkdown = ''
    vm.uploadedFiles = [{
      originalName: 'notes.txt',
      fileUrl: '/api/resource/files/1/notes.txt',
      fileSize: 12,
      fileType: 'text/plain'
    }]
    await nextTick()

    await nextTick()
    expect(vm.canSubmit).toBe(true)
    await vm.submit()

    expect(mocks.submitResource).toHaveBeenCalledWith(expect.objectContaining({
      title: '校园摄影',
      contentScene: 'GALLERY',
      cat: undefined,
      contentMarkdown: '',
      attachments: expect.arrayContaining([
        expect.objectContaining({ originalName: 'notes.txt' })
      ])
    }))
  })

  it('allows a blog post to contain both text and attachments', async () => {
    const wrapper = shallowMount(PublishView)
    const vm = wrapper.vm as any

    vm.form.scene = 'BLOG'
    vm.form.title = '学习笔记'
    vm.form.cat = '编程'
    vm.form.contentMarkdown = '正文内容'
    vm.uploadedFiles = [{
      originalName: 'old-draft.txt',
      fileUrl: '/api/resource/files/1/old-draft.txt',
      fileSize: 12,
      fileType: 'text/plain'
    }]
    await nextTick()

    expect(vm.canSubmit).toBe(true)
    await vm.submit()

    expect(mocks.submitResource).toHaveBeenCalledWith(expect.objectContaining({
      contentScene: 'BLOG',
      contentMarkdown: '正文内容',
      attachments: expect.arrayContaining([
        expect.objectContaining({ originalName: 'old-draft.txt' })
      ])
    }))
  })
})
