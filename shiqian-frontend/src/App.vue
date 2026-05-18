<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, User, Switch } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const store = useAppStore()

onMounted(() => {
  store.loadHomeData().catch(() => undefined)
})

const studentLinks = [
  { path: '/home', label: '首页' },
  { path: '/plaza', label: '资源广场' },
  { path: '/categories', label: '分类浏览' },
  { path: '/publish', label: '发布资源' },
  { path: '/favorites', label: '我的收藏' },
  { path: '/mine', label: '我的发布' }
]

const adminLinks = [
  { path: '/admin', label: '后台首页' },
  { path: '/audit', label: '资源审核' },
  { path: '/resource-admin', label: '资源管理' },
  { path: '/category-admin', label: '分类管理' },
  { path: '/user-admin', label: '用户管理' }
]

const navLinks = computed(() => store.role === 'admin' ? adminLinks : studentLinks)

function switchRole() {
  const next = store.role === 'student' ? 'admin' : 'student'
  store.setRole(next)
  router.push(next === 'admin' ? '/admin' : '/home')
}

function goLogin() {
  router.push('/login')
}

function goPublish() {
  store.setRole('student')
  router.push('/publish')
}
</script>

<template>
  <el-container class="app-shell">
    <el-header class="topbar">
      <div class="nav-inner">
        <router-link class="brand" to="/home" @click="store.setRole('student')">
          <span class="logo">迁</span>
          <span>时迁校园</span>
        </router-link>
        <nav class="nav-links">
          <router-link
            v-for="item in navLinks"
            :key="item.path"
            :to="item.path"
            :class="{ active: route.path === item.path }"
          >
            {{ item.label }}
          </router-link>
        </nav>
        <div class="nav-actions">
          <el-button :icon="Switch" @click="switchRole">{{ store.role === 'student' ? '学生端' : '管理端' }}</el-button>
          <el-button v-if="!store.logged" :icon="User" @click="goLogin">登录</el-button>
          <el-button v-else @click="store.logout()">退出</el-button>
          <el-button type="primary" :icon="Plus" @click="goPublish">发布资源</el-button>
        </div>
      </div>
    </el-header>
    <el-main class="main-content">
      <router-view />
    </el-main>
    <el-footer class="footer">
      <span>© 2026 时迁校园资源共享平台 · Vue3 + TypeScript + Element Plus + Pinia</span>
      <span>统一用户端 / 后台端 / 页面逻辑 / 视觉系统</span>
    </el-footer>
  </el-container>
</template>
