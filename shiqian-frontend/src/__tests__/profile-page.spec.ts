import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, vi } from 'vitest';
import { userApi } from '../api/user';
import { useAuthStore } from '../stores/auth';
import ProfileView from '../views/ProfileView.vue';

vi.mock('../api/user', () => ({
  userApi: {
    updateCurrentUser: vi.fn()
  }
}));

function mountProfile() {
  return mount(ProfileView, {
    global: {
      stubs: {
        RouterLink: {
          template: '<a><slot /></a>'
        }
      }
    }
  });
}

describe('profile page', () => {
  beforeEach(() => {
    window.localStorage.clear();
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('shows login entry when user is anonymous', () => {
    const wrapper = mountProfile();

    expect(wrapper.text()).toContain('请先登录后查看和更新个人资料');
  });

  it('renders authenticated user summary', () => {
    const authStore = useAuthStore();
    authStore.setSession(
      { accessToken: 'access-token', refreshToken: 'refresh-token' },
      { userId: 1, username: 'alice', nickname: 'Alice', role: 'USER' }
    );

    const wrapper = mountProfile();

    expect(wrapper.text()).toContain('alice');
    expect(wrapper.text()).toContain('USER');
  });

  it('blocks invalid profile boundary values', async () => {
    const authStore = useAuthStore();
    authStore.setSession(
      { accessToken: 'access-token', refreshToken: 'refresh-token' },
      { userId: 1, username: 'alice', role: 'USER' }
    );
    const wrapper = mountProfile();

    await wrapper.find('input[name="nickname"]').setValue('a'.repeat(21));
    await wrapper.find('form').trigger('submit');

    expect(wrapper.text()).toContain('昵称不能超过20个字符');
    expect(userApi.updateCurrentUser).not.toHaveBeenCalled();
  });

  it('submits profile update and shows success', async () => {
    vi.mocked(userApi.updateCurrentUser).mockResolvedValue(undefined);
    const authStore = useAuthStore();
    authStore.setSession(
      { accessToken: 'access-token', refreshToken: 'refresh-token' },
      { userId: 1, username: 'alice', role: 'USER' }
    );
    const wrapper = mountProfile();

    await wrapper.find('input[name="nickname"]').setValue('Alice');
    await wrapper.find('input[name="email"]').setValue('alice@example.com');
    await wrapper.find('input[name="phone"]').setValue('13800138000');
    await wrapper.find('input[name="avatar"]').setValue('https://example.com/a.png');
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(userApi.updateCurrentUser).toHaveBeenCalledWith({
      nickname: 'Alice',
      email: 'alice@example.com',
      phone: '13800138000',
      avatar: 'https://example.com/a.png'
    });
    expect(wrapper.text()).toContain('资料已更新');
  });
});
