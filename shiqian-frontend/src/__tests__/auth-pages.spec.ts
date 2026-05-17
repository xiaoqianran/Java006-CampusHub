import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, vi } from 'vitest';
import LoginView from '../views/LoginView.vue';
import RegisterView from '../views/RegisterView.vue';
import { userApi } from '../api/user';
import { useAuthStore } from '../stores/auth';

vi.mock('../api/user', () => ({
  userApi: {
    login: vi.fn(),
    register: vi.fn()
  }
}));

const push = vi.fn();

vi.mock('vue-router', () => ({
  RouterLink: {
    template: '<a><slot /></a>'
  },
  useRouter: () => ({ push })
}));

describe('auth pages', () => {
  beforeEach(() => {
    window.localStorage.clear();
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('blocks empty login submission', async () => {
    const wrapper = mount(LoginView, {
      global: { stubs: { RouterLink: true } }
    });

    await wrapper.find('form').trigger('submit');

    expect(wrapper.text()).toContain('用户名不能为空');
    expect(userApi.login).not.toHaveBeenCalled();
  });

  it('stores session after login success', async () => {
    vi.mocked(userApi.login).mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      userId: 1,
      username: 'alice',
      nickname: 'Alice',
      role: 'USER'
    });
    const wrapper = mount(LoginView, {
      global: { stubs: { RouterLink: true } }
    });

    await wrapper.find('input[name="username"]').setValue('alice');
    await wrapper.find('input[name="password"]').setValue('123456');
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    const authStore = useAuthStore();
    expect(authStore.isAuthenticated).toBe(true);
    expect(authStore.user?.username).toBe('alice');
    expect(push).toHaveBeenCalledWith('/resources');
  });

  it('blocks invalid register boundary values', async () => {
    const wrapper = mount(RegisterView, {
      global: { stubs: { RouterLink: true } }
    });

    await wrapper.find('input[name="username"]').setValue('abc');
    await wrapper.find('input[name="password"]').setValue('12345');
    await wrapper.find('form').trigger('submit');

    expect(wrapper.text()).toContain('用户名需为4-20位字母、数字或下划线');
    expect(userApi.register).not.toHaveBeenCalled();
  });

  it('submits register form and shows success', async () => {
    vi.mocked(userApi.register).mockResolvedValue(undefined);
    const wrapper = mount(RegisterView, {
      global: { stubs: { RouterLink: true } }
    });

    await wrapper.find('input[name="username"]').setValue('alice_01');
    await wrapper.find('input[name="password"]').setValue('123456');
    await wrapper.find('input[name="email"]').setValue('alice@example.com');
    await wrapper.find('input[name="phone"]').setValue('13800138000');
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(userApi.register).toHaveBeenCalledWith({
      username: 'alice_01',
      password: '123456',
      nickname: undefined,
      email: 'alice@example.com',
      phone: '13800138000'
    });
    expect(wrapper.text()).toContain('注册成功，请登录');
  });
});
