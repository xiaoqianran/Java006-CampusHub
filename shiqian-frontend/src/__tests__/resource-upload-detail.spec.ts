import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, vi } from 'vitest';
import { resourceApi } from '../api/resource';
import ResourceDetailView from '../views/ResourceDetailView.vue';
import ResourceUploadView from '../views/ResourceUploadView.vue';

vi.mock('../api/resource', () => ({
  resourceApi: {
    createResource: vi.fn(),
    getResource: vi.fn(),
    downloadResource: vi.fn()
  }
}));

describe('resource upload and detail pages', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('blocks invalid upload form', async () => {
    const wrapper = mount(ResourceUploadView, {
      global: { stubs: { RouterLink: true } }
    });

    await wrapper.find('form').trigger('submit');

    expect(wrapper.text()).toContain('资源标题不能为空');
    expect(resourceApi.createResource).not.toHaveBeenCalled();
  });

  it('submits resource create form', async () => {
    vi.mocked(resourceApi.createResource).mockResolvedValue(undefined);
    const wrapper = mount(ResourceUploadView, {
      global: { stubs: { RouterLink: true } }
    });

    await wrapper.find('input[name="title"]').setValue('Java 笔记');
    await wrapper.find('textarea[name="description"]').setValue('基础知识');
    await wrapper.find('input[name="categoryId"]').setValue('1');
    await wrapper.find('input[name="fileUrl"]').setValue('https://example.com/java.pdf');
    await wrapper.find('input[name="fileSize"]').setValue('1024');
    await wrapper.find('input[name="fileType"]').setValue('application/pdf');
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(resourceApi.createResource).toHaveBeenCalledWith({
      title: 'Java 笔记',
      description: '基础知识',
      categoryId: 1,
      fileUrl: 'https://example.com/java.pdf',
      fileSize: 1024,
      fileType: 'application/pdf'
    });
    expect(wrapper.text()).toContain('资源已提交审核');
  });

  it('loads resource detail by route id', async () => {
    vi.mocked(resourceApi.getResource).mockResolvedValue({
      id: 1,
      userId: 1,
      title: 'Java 笔记',
      description: '基础知识',
      categoryId: 1,
      fileUrl: 'https://example.com/java.pdf',
      fileSize: 1024,
      fileType: 'application/pdf',
      downloadCount: 3,
      version: 1,
      status: 1
    });

    const wrapper = mount(ResourceDetailView, { props: { id: '1' } });
    await flushPromises();

    expect(resourceApi.getResource).toHaveBeenCalledWith(1);
    expect(wrapper.text()).toContain('Java 笔记');
  });

  it('records resource download', async () => {
    vi.mocked(resourceApi.getResource).mockResolvedValue({
      id: 1,
      userId: 1,
      title: 'Java 笔记',
      fileUrl: 'https://example.com/java.pdf',
      fileSize: 1024,
      fileType: 'application/pdf',
      downloadCount: 3,
      version: 1,
      status: 1
    });
    vi.mocked(resourceApi.downloadResource).mockResolvedValue(undefined);

    const wrapper = mount(ResourceDetailView, { props: { id: '1' } });
    await flushPromises();
    await wrapper.find('button').trigger('click');
    await flushPromises();

    expect(resourceApi.downloadResource).toHaveBeenCalledWith(1);
    expect(wrapper.text()).toContain('下载统计已更新');
  });

  it('shows invalid id error', async () => {
    const wrapper = mount(ResourceDetailView, { props: { id: 'abc' } });
    await flushPromises();

    expect(wrapper.text()).toContain('资源ID不合法');
    expect(resourceApi.getResource).not.toHaveBeenCalled();
  });
});
