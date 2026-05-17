<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const store = useAppStore()
const links = [
  { path: '/admin', label: '后台首页' },
  { path: '/audit', label: '资源审核' },
  { path: '/resource-admin', label: '资源管理' },
  { path: '/category-admin', label: '分类管理' },
  { path: '/user-admin', label: '用户管理' }
]

function backToStudent() {
  store.setRole('student')
  router.push('/home')
}
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-sidebar panel">
      <el-menu :default-active="route.path" router>
        <el-menu-item v-for="item in links" :key="item.path" :index="item.path">{{ item.label }}</el-menu-item>
      </el-menu>
      <el-divider />
      <el-button class="full" @click="backToStudent">返回学生端</el-button>
    </aside>
    <section class="admin-main">
      <slot />
    </section>
  </div>
</template>
