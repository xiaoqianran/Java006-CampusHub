import { mount } from '@vue/test-utils';
import App from '../App.vue';
import AppShell from '../components/AppShell.vue';

describe('AppShell', () => {
  it('renders platform title', () => {
    const wrapper = mount(AppShell);

    expect(wrapper.text()).toContain('时迁校园资源共享平台');
  });

  it('renders three capability summaries', () => {
    const wrapper = mount(AppShell);

    expect(wrapper.findAll('.summary-grid article')).toHaveLength(3);
  });

  it('mounts root app component', () => {
    const wrapper = mount(App);

    expect(wrapper.findComponent(AppShell).exists()).toBe(true);
  });
});
