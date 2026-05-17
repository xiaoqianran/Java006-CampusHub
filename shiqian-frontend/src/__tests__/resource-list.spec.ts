import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, vi } from 'vitest';
import { resourceApi } from '../api/resource';
import ResourceListView from '../views/ResourceListView.vue';

vi.mock('../api/resource', () => ({
  resourceApi: {
    pageResources: vi.fn(),
    listCategoryTree: vi.fn()
  }
}));

function mountList() {
  return mount(ResourceListView, {
    global: {
      stubs: {
        RouterLink: {
          template: '<a><slot /></a>'
        }
      }
    }
  });
}

describe('resource list page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(resourceApi.listCategoryTree).mockResolvedValue([
      { id: 1, parentId: 0, name: '计算机', children: [{ id: 2, parentId: 1, name: 'Java' }] }
    ]);
  });

  it('loads categories and resources on mounted', async () => {
    vi.mocked(resourceApi.pageResources).mockResolvedValue({
      records: [
        {
          id: 1,
          userId: 1,
          title: 'Java 笔记',
          description: '基础知识',
          categoryId: 2,
          fileUrl: 'https://example.com/java.pdf',
          fileSize: 1024,
          fileType: 'application/pdf',
          downloadCount: 3,
          version: 1,
          status: 1
        }
      ],
      total: 1,
      current: 1,
      size: 10
    });

    const wrapper = mountList();
    await flushPromises();

    expect(wrapper.text()).toContain('Java 笔记');
    expect(wrapper.text()).toContain('计算机');
    expect(resourceApi.pageResources).toHaveBeenCalledWith({ page: 1, size: 10 });
  });

  it('submits keyword search from first page', async () => {
    vi.mocked(resourceApi.pageResources).mockResolvedValue({
      records: [],
      total: 0,
      current: 1,
      size: 10
    });
    const wrapper = mountList();
    await flushPromises();

    await wrapper.find('input[name="keyword"]').setValue('算法');
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(resourceApi.pageResources).toHaveBeenLastCalledWith({
      page: 1,
      size: 10,
      keyword: '算法',
      categoryId: undefined
    });
  });

  it('filters by category', async () => {
    vi.mocked(resourceApi.pageResources).mockResolvedValue({
      records: [],
      total: 0,
      current: 1,
      size: 10
    });
    const wrapper = mountList();
    await flushPromises();

    await wrapper.find('select[name="categoryId"]').setValue('2');
    await flushPromises();

    expect(resourceApi.pageResources).toHaveBeenLastCalledWith({
      page: 1,
      size: 10,
      keyword: undefined,
      categoryId: 2
    });
  });

  it('renders error state when resource request fails', async () => {
    vi.mocked(resourceApi.pageResources).mockRejectedValue(new Error('服务异常'));
    const wrapper = mountList();
    await flushPromises();

    expect(wrapper.text()).toContain('服务异常');
  });
});
