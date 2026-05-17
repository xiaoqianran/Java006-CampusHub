<template>
  <div class="min-h-screen flex flex-col bg-[#f8f5f0] text-[#172026]">
    <!-- 世界级顶部导航 -->
    <header class="sticky top-0 z-50 bg-white/95 backdrop-blur border-b border-[#e5e0d8]">
      <div class="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
        <!-- Logo + 品牌 -->
        <router-link to="/" class="flex items-center gap-3 group">
          <div class="w-9 h-9 rounded-2xl bg-[#0f766e] flex items-center justify-center text-white text-2xl font-bold tracking-[-1.5px]">
            时
          </div>
          <div>
            <div class="font-semibold text-xl tracking-tight group-hover:text-[#0f766e] transition-colors">时迁</div>
            <div class="text-[10px] text-[#8a7155] -mt-1">CAMPUS KNOWLEDGE COMMONS</div>
          </div>
        </router-link>

        <!-- 全局搜索 (Cmd+K 提示) -->
        <div class="hidden md:flex flex-1 max-w-md mx-8">
          <div 
            @click="goToSearch"
            class="w-full flex items-center gap-3 px-4 py-2 bg-[#f8f5f0] hover:bg-white border border-[#e5e0d8] rounded-2xl text-sm text-[#5c4630] cursor-pointer transition-all"
          >
            <Search class="w-4 h-4" />
            <span>搜索全站资源 · 支持中文</span>
            <span class="ml-auto text-[10px] font-mono bg-white px-1.5 py-px rounded border">⌘K</span>
          </div>
        </div>

        <!-- 主导航 -->
        <nav class="flex items-center gap-2 text-sm">
          <router-link to="/resources" class="px-4 py-1.5 rounded-xl hover:bg-[#f8f5f0] transition-colors" active-class="nav-active">资源广场</router-link>
          <router-link v-if="authStore.isAuthenticated" to="/publish" class="px-4 py-1.5 rounded-xl hover:bg-[#f8f5f0] transition-colors">发布资源</router-link>

          <!-- 用户区 -->
          <div v-if="authStore.isAuthenticated" class="relative ml-2">
            <button @click="userMenuOpen = !userMenuOpen" class="flex items-center gap-2 pl-2 pr-3 py-1 rounded-2xl hover:bg-[#f8f5f0]">
              <div class="w-7 h-7 rounded-full bg-[#0f766e]/10 flex items-center justify-center text-[#0f766e] text-xs font-medium">
                {{ userInitial }}
              </div>
              <span class="hidden sm:block text-sm font-medium">{{ authStore.user?.nickname || authStore.user?.username }}</span>
            </button>

            <!-- 下拉菜单 -->
            <div v-if="userMenuOpen" class="absolute right-0 mt-2 w-52 bg-white rounded-2xl shadow-xl border border-[#e5e0d8] py-1 text-sm z-50">
              <router-link to="/profile" class="block px-4 py-2 hover:bg-[#f8f5f0]">个人中心</router-link>
              <router-link v-if="isAdmin" to="/admin/audit" class="block px-4 py-2 hover:bg-[#f8f5f0] text-[#0f766e]">审核中心</router-link>
              <div class="h-px bg-[#e5e0d8] my-1"></div>
              <button @click="handleLogout" class="w-full text-left px-4 py-2 text-rose-600 hover:bg-rose-50">退出登录</button>
            </div>
          </div>

          <router-link v-else to="/login" class="ml-2 px-5 py-1.5 rounded-2xl bg-[#0f766e] text-white text-sm font-medium hover:bg-[#0c5f57] transition-colors">
            登录 / 注册
          </router-link>
        </nav>
      </div>
    </header>

    <!-- 主内容区 -->
    <main class="flex-1">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <!-- 诗意页脚 -->
    <footer class="border-t border-[#e5e0d8] py-8 text-center text-xs text-[#8a7155]">
      <div class="max-w-7xl mx-auto px-6">
        时迁校园资源共享平台 · 让每一份笔记，照亮更多求知的路<br>
        <span class="opacity-60">Spring Cloud · Elasticsearch · RabbitMQ · Vue3 · 2025–2026</span>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { Search } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()
const userMenuOpen = ref(false)
const userInitial = computed(() => (authStore.user?.nickname || authStore.user?.username || 'U')[0].toUpperCase())
const isAdmin = computed(() => authStore.user?.role === 'ADMIN')

function goToSearch() {
  router.push('/resources?focusSearch=1')
}

function handleLogout() {
  authStore.clearSession()
  userMenuOpen.value = false
  ElMessage.success('已退出登录')
  router.push('/')
}

// 点击外部关闭菜单
function closeMenu(e: MouseEvent) {
  if (!(e.target as HTMLElement)?.closest('.relative')) {
    userMenuOpen.value = false
  }
}

onMounted(() => document.addEventListener('click', closeMenu))
onUnmounted(() => document.removeEventListener('click', closeMenu))
</script>

<style scoped>
.page-enter-active,
.page-leave-active {
  transition: opacity 0.2s ease;
}
.page-enter-from,
.page-leave-to {
  opacity: 0;
}
</style>