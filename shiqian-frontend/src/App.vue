<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, User, Setting, Sunny, Moon } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const store = useAppStore()

onMounted(() => {
  store.initTheme()
  store.loadHomeData({ includePersonal: true }).catch(() => undefined)
})

const primaryLinks = [
  { path: '/home', label: '首页' },
  { path: '/resources', label: '资源中心' },
  { path: '/publish', label: '发布资源' }
]

const canAccessAdmin = computed(() => store.currentUser?.role === 'ADMIN')
const personalActive = computed(() => ['/mine', '/favorites', '/profile'].includes(route.path))

function goLogin() {
  router.push('/login')
}

function goAdmin() {
  router.push('/admin')
}

function isLinkActive(path: string) {
  if (path === '/resources') {
    return route.path === '/resources' || route.path.startsWith('/detail/')
  }
  return route.path === path
}

function handlePersonalCommand(command: string) {
  if (command === 'logout') {
    store.logout()
    router.push('/home')
    return
  }
  router.push(command)
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
            v-for="item in primaryLinks"
            :key="item.path"
            :to="item.path"
            :class="{ active: isLinkActive(item.path) }"
          >
            {{ item.label }}
          </router-link>
        </nav>
        <div class="nav-actions">
          <el-button v-if="canAccessAdmin" :icon="Setting" @click="goAdmin">管理端</el-button>
          <el-button v-if="!store.logged" :icon="User" @click="goLogin">登录</el-button>
          <el-dropdown v-else trigger="click" @command="handlePersonalCommand">
            <el-button :icon="User" :class="{ 'is-context-active': personalActive }">
              个人中心
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="/mine">我的发布</el-dropdown-item>
                <el-dropdown-item command="/favorites">我的收藏</el-dropdown-item>
                <el-dropdown-item command="/profile">个人资料</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-button
            :icon="store.isDark ? Sunny : Moon"
            circle
            @click="store.toggleTheme"
            :title="store.isDark ? '切换到浅色模式' : '切换到深色模式'"
          />
        </div>
      </div>
    </el-header>
    <el-main class="main-content">
      <router-view />
    </el-main>
    <el-footer class="footer">
      <span>© 2026 时迁校园资源共享平台</span>
      <span>让校园资料更容易被发现、分享和管理</span>
    </el-footer>
  </el-container>
</template>
