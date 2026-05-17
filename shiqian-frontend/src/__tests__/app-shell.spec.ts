import { mount } from '@vue/test-utils';
import App from '../App.vue';
import AppShell from '../components/AppShell.vue';

const global = {
  stubs: {
    RouterLink: {
      template: '<a><slot /></a>'
    },
    RouterView: {
      template: '<div />'
    }
  }
};

describe('AppShell', () => {
  it('renders platform title', () => {
    const wrapper = mount(AppShell, { global });

    expect(wrapper.text()).toContain('时迁校园资源共享平台');
  });

  it('renders primary navigation', () => {
    const wrapper = mount(AppShell, { global });

    expect(wrapper.findAll('.nav-tabs a')).toHaveLength(4);
  });

  it('mounts root app component', () => {
    const wrapper = mount(App, { global });

    expect(wrapper.findComponent(AppShell).exists()).toBe(true);
  });
});
