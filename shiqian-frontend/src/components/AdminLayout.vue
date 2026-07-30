<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const groups = [
  {
    label: '工作台',
    links: [{ path: '/admin', label: '数据概览' }]
  },
  {
    label: '内容管理',
    links: [
      { path: '/admin/audit', label: '审核工作台' },
      { path: '/admin/resources', label: '内容管理' },
      { path: '/admin/recycle-bin', label: '回收站' }
    ]
  },
  {
    label: '平台管理',
    links: [
      { path: '/admin/categories', label: '分类管理' },
      { path: '/admin/tags', label: '标签管理' },
      { path: '/admin/users', label: '用户管理' },
      { path: '/admin/logs', label: '操作日志' }
    ]
  }
]

function goHome() {
  router.push('/home')
}
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-sidebar panel">
      <el-menu :default-active="route.path" router>
        <template v-for="group in groups" :key="group.label">
          <div class="admin-menu-label">{{ group.label }}</div>
          <el-menu-item v-for="item in group.links" :key="item.path" :index="item.path">
            {{ item.label }}
          </el-menu-item>
        </template>
      </el-menu>
      <el-divider />
      <el-button class="full" @click="goHome">返回首页</el-button>
    </aside>
    <section class="admin-main">
      <slot />
    </section>
  </div>
</template>

<style scoped>
.admin-menu-label {
  padding: 18px 20px 6px;
  color: var(--text-secondary, #909399);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .08em;
}
</style>
