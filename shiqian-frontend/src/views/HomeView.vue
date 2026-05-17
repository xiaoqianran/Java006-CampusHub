<template>
  <div>
    <!-- 英雄区 - 世界级视觉 -->
    <div class="relative bg-[#0f766e] text-white overflow-hidden">
      <div class="absolute inset-0 bg-[radial-gradient(#ffffff15_0.8px,transparent_1px)] bg-[length:4px_4px]"></div>
      <div class="max-w-5xl mx-auto px-6 pt-16 pb-20 text-center relative">
        <div class="inline-flex items-center gap-2 px-4 py-1 rounded-full bg-white/10 text-sm mb-6">
          <span>2025–2026 高校知识共享计划</span>
        </div>
        <h1 class="text-6xl md:text-7xl font-semibold tracking-tighter mb-4">
          让每一份笔记<br>照亮更多求知的路
        </h1>
        <p class="max-w-md mx-auto text-xl text-white/80 mb-10">
          时迁 · 校园资源共享平台<br>全校学生共建的知识森林
        </p>

        <!-- 全局搜索入口 -->
        <div class="max-w-xl mx-auto">
          <div class="flex bg-white rounded-3xl p-2 shadow-2xl">
            <input
              v-model="searchKeyword"
              @keyup.enter="doGlobalSearch"
              placeholder="搜索笔记、实验报告、课件... 支持中文全文检索"
              class="flex-1 px-6 text-lg text-[#172026] placeholder:text-[#8a7155] outline-none bg-transparent"
            />
            <button
              @click="doGlobalSearch"
              class="px-8 py-3 rounded-2xl bg-[#0f766e] text-white font-medium flex items-center gap-2 hover:bg-[#0c5f57] active:scale-[0.985] transition-all"
            >
              <Search class="w-5 h-5" /> 搜索
            </button>
          </div>
          <div class="text-xs text-white/60 mt-3">已收录 12,847 份资源 · Elasticsearch 毫秒级响应</div>
        </div>
      </div>
    </div>

    <!-- 分类精选 -->
    <div class="max-w-7xl mx-auto px-6 py-14">
      <div class="flex justify-between items-end mb-8">
        <div>
          <div class="uppercase tracking-[2px] text-xs text-[#0f766e] font-medium">DISCOVER BY CATEGORY</div>
          <div class="text-4xl font-semibold tracking-tight mt-1">按知识领域探索</div>
        </div>
        <router-link to="/resources" class="text-sm flex items-center gap-1 text-[#0f766e] hover:underline">全部分类 →</router-link>
      </div>

      <div class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4" v-if="topCategories.length">
        <router-link
          v-for="cat in topCategories"
          :key="cat.id"
          :to="`/resources?categoryId=${cat.id}`"
          class="shiqian-card p-5 group"
        >
          <div class="text-3xl mb-4 opacity-80 group-hover:scale-110 transition">📚</div>
          <div class="font-semibold text-lg">{{ cat.name }}</div>
          <div class="text-xs text-[#8a7155] mt-1">查看该分类下的全部资源</div>
        </router-link>
      </div>
      <div v-else class="text-[#8a7155]">分类加载中...</div>
    </div>

    <!-- 最新资源推荐 -->
    <div class="bg-white border-t border-[#e5e0d8]">
      <div class="max-w-7xl mx-auto px-6 py-14">
        <div class="flex items-center justify-between mb-8">
          <div>
            <div class="text-[#0f766e] text-sm tracking-widest">JUST ADDED</div>
            <div class="text-4xl font-semibold tracking-tight">最新加入的知识</div>
          </div>
          <router-link to="/resources" class="text-[#0f766e] text-sm hover:underline">浏览全部资源 →</router-link>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <div v-for="res in latestResources" :key="res.id" class="shiqian-card p-5 flex flex-col">
            <div class="flex-1">
              <div class="font-semibold leading-tight line-clamp-2 mb-2">{{ res.title }}</div>
              <div class="text-sm text-[#5c4630] line-clamp-3">{{ res.description || '暂无描述' }}</div>
            </div>
            <div class="flex items-center justify-between text-xs mt-4 pt-4 border-t text-[#8a7155]">
              <span>贡献者 #{{ res.userId }}</span>
              <span class="font-mono">{{ res.downloadCount }} 次下载</span>
            </div>
            <router-link :to="`/resources/${res.id}`" class="mt-4 text-sm text-[#0f766e] hover:underline">查看详情 →</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from 'lucide-vue-next'
import { resourceApi } from '../api/resource'
import { useCategoryStore } from '../stores/category'
import type { ResourceItem } from '../types/resource'

const router = useRouter()
const categoryStore = useCategoryStore()
const searchKeyword = ref('')
const latestResources = ref<ResourceItem[]>([])

const topCategories = computed(() => categoryStore.categoryTree.slice(0, 6))

async function loadData() {
  try {
    await categoryStore.loadTree()
    const resPage = await resourceApi.pageResources({ page: 1, size: 8 })
    latestResources.value = (resPage?.records || []).filter(r => r.status === 1).slice(0, 4)
  } catch (e) {
    // 优雅降级
    latestResources.value = []
  }
}

function doGlobalSearch() {
  if (!searchKeyword.value.trim()) return
  router.push({ path: '/resources', query: { keyword: searchKeyword.value.trim() } })
}

onMounted(loadData)
</script>