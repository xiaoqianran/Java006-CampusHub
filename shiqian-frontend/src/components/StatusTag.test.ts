import { describe, it, expect } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import StatusTag from './StatusTag.vue'

describe('StatusTag', () => {
  it('renders success tag for 已发布 and 正常', () => {
    const w1 = shallowMount(StatusTag, { props: { status: '已发布' } })
    expect(w1.text()).toContain('已发布')
    expect(w1.html()).toContain('el-tag') // stubbed but present

    const w2 = shallowMount(StatusTag, { props: { status: '正常' } })
    expect(w2.text()).toContain('正常')
  })

  it('renders warning tag for 待审核', () => {
    const wrapper = shallowMount(StatusTag, { props: { status: '待审核' } })
    expect(wrapper.text()).toContain('待审核')
  })

  it('renders danger tag for rejected and disabled statuses', () => {
    const w1 = shallowMount(StatusTag, { props: { status: '已拒绝' } })
    expect(w1.text()).toContain('已拒绝')

    const w2 = shallowMount(StatusTag, { props: { status: '禁用' } })
    expect(w2.text()).toContain('禁用')
  })
})
