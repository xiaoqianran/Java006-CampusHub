<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, User, Setting, Sunny, Moon } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const store = useAppStore()

onMounted(() => {
  store.initTheme()
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

const canAccessAdmin = computed(() => store.currentUser?.role === 'ADMIN')

function goLogin() {
  router.push('/login')
}

function goPublish() {
  router.push('/publish')
}

function goAdmin() {
  router.push('/admin')
}

function logout() {
  store.logout()
  router.push('/home')
}
</script>

<template>
  <el-container class="app-shell">
    <el-header class="topbar">
      <div class="nav-inner">
        <router-link class="brand" to="/home">
          <span class="logo">迁</span>
          <span>时迁校园</span>
        </router-link>
        <nav class="nav-links">
          <router-link
            v-for="item in studentLinks"
            :key="item.path"
            :to="item.path"
            :class="{ active: route.path === item.path }"
          >
            {{ item.label }}
          </router-link>
        </nav>
        <div class="nav-actions">
          <el-button v-if="canAccessAdmin" :icon="Setting" @click="goAdmin">管理端</el-button>
          <el-button v-if="!store.logged" :icon="User" @click="goLogin">登录</el-button>
          <el-button v-else @click="logout">退出</el-button>

          <!-- 主题切换按钮（Search-First 技能强制要求） -->
          <el-button
            :icon="store.isDark ? Sunny : Moon"
            circle
            @click="store.toggleTheme"
            :title="store.isDark ? '切换到浅色模式' : '切换到深色模式'"
          />

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
