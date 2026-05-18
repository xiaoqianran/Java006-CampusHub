<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const links = [
  { path: '/admin', label: '后台首页' },
  { path: '/admin/audit', label: '资源审核' },
  { path: '/admin/resources', label: '资源管理' },
  { path: '/admin/categories', label: '分类管理' },
  { path: '/admin/users', label: '用户管理' }
]

function backToStudent() {
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
