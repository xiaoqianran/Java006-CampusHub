<template>
  <div class="max-w-7xl mx-auto px-6 py-8">
    <!-- 页面头 -->
    <div class="flex items-end justify-between mb-6">
      <div>
        <div class="uppercase text-xs tracking-[3px] text-[#0f766e]">EXPLORE THE COMMONS</div>
        <h1 class="text-5xl font-semibold tracking-[-1.5px]">资源广场</h1>
      </div>
      <div class="text-sm text-[#5c4630]">
        共 <span class="font-mono text-[#0f766e]">{{ total }}</span> 份资源
      </div>
    </div>

    <div class="flex gap-6">
      <!-- 左侧分类树 -->
      <div class="w-64 hidden lg:block">
        <div class="sticky top-20">
          <div class="font-medium text-sm mb-3 px-3 text-[#0f766e]">知识分类</div>
          <div class="shiqian-card p-2 max-h-[70vh] overflow-auto">
            <div 
              @click="selectCategory(undefined)"
              class="px-3 py-2 rounded-2xl mb-0.5 cursor-pointer"
              :class="!query.categoryId ? 'bg-[#0f766e] text-white' : 'hover:bg-[#f0f9f7]'"
            >
              全部资源
            </div>
            <CategoryTree 
              :tree="categoryStore.categoryTree" 
              :selected-id="query.categoryId"
              @select="selectCategory"
            />
          </div>
          <div class="text-[10px] text-[#8a7155] mt-3 px-3">分类数据来自 Redis 缓存（30分钟）</div>
        </div>
      </div>

      <!-- 主内容区 -->
      <div class="flex-1">
        <!-- 工具栏 -->
        <div class="flex flex-wrap items-center gap-3 mb-5">
          <!-- 搜索 -->
          <div class="flex-1 min-w-[240px]">
            <el-input
              v-model="query.keyword"
              placeholder="搜索标题或描述（支持中文全文检索）"
              clearable
              size="large"
              @input="onSearchInput"
              @clear="loadResources"
            >
              <template #prefix><Search :size="16" class="text-[#8a7155]" /></template>
            </el-input>
          </div>

          <!-- 排序 -->
          <el-select v-model="sortMode" size="large" style="width: 160px" @change="loadResources">
            <el-option label="最新发布" value="new" />
            <el-option label="下载最多" value="hot" />
            <el-option label="版本最高" value="version" />
          </el-select>

          <!-- 每页数量 -->
          <el-select v-model="query.size" size="large" style="width: 110px" @change="loadResources">
            <el-option :value="10" label="10 / 页" />
            <el-option :value="20" label="20 / 页" />
            <el-option :value="40" label="40 / 页" />
          </el-select>

          <el-button type="primary" size="large" @click="goPublish" class="!px-6">
            + 发布资源
          </el-button>
        </div>

        <!-- 加载状态 -->
        <div v-if="loading" class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-5">
          <div v-for="i in 8" :key="i" class="shiqian-card h-[260px] animate-pulse bg-[#f8f5f0]" />
        </div>

        <!-- 资源网格 -->
        <div v-else-if="resources.length" class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-5">
          <ResourceCard v-for="res in sortedResources" :key="res.id" :resource="res" />
        </div>

        <!-- 空状态 -->
        <EmptyState 
          v-else
          title="这里暂时没有资源" 
          description="试试更换分类或关键词，或者成为第一个贡献者！"
        >
          <template #action>
            <router-link to="/publish" class="mt-6 inline-block px-6 py-2 rounded-2xl bg-[#0f766e] text-white text-sm">去发布资源</router-link>
          </template>
        </EmptyState>

        <!-- 分页 -->
        <div v-if="total > query.size" class="mt-8 flex justify-center">
          <el-pagination
            v-model:current-page="query.page"
            :page-size="query.size"
            :total="total"
            layout="prev, pager, next, jumper"
            @current-change="loadResources"
            background
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { resourceApi } from '../api/resource'
import { useCategoryStore } from '../stores/category'
import { useFavoritesStore } from '../stores/favorites'
import type { ResourceItem, ResourceQuery } from '../types/resource'
import ResourceCard from '../components/ResourceCard.vue'
import CategoryTree from '../components/CategoryTree.vue'
import EmptyState from '../components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const categoryStore = useCategoryStore()
const favStore = useFavoritesStore()

const loading = ref(false)
const resources = ref<ResourceItem[]>([])
const total = ref(0)

const query = reactive<ResourceQuery>({
  page: 1,
  size: 20,
  keyword: '',
  categoryId: undefined
})

const sortMode = ref<'new' | 'hot' | 'version'>('new')

// 客户端排序（后端暂无 sort 参数）
const sortedResources = computed(() => {
  let list = [...resources.value]
  if (sortMode.value === 'hot') {
    list.sort((a, b) => (b.downloadCount || 0) - (a.downloadCount || 0))
  } else if (sortMode.value === 'version') {
    list.sort((a, b) => (b.version || 1) - (a.version || 1))
  } else {
    // 默认最新（createTime 倒序）
    list.sort((a, b) => {
      const ta = a.createTime ? new Date(a.createTime).getTime() : 0
      const tb = b.createTime ? new Date(b.createTime).getTime() : 0
      return tb - ta
    })
  }
  // 公开视图：优先显示已通过的，自己的待审核也显示（简单处理）
  return list
})

async function loadResources() {
  loading.value = true
  try {
    const params: any = { ...query }
    if (!params.categoryId) delete params.categoryId
    if (!params.keyword) delete params.keyword

    const res = await resourceApi.pageResources(params)
    resources.value = res.records || []
    total.value = res.total || 0

    // 同步收藏状态（对已登录用户）
    const auth = (await import('../stores/auth')).useAuthStore()
    if (auth.isAuthenticated) {
      resources.value.forEach(r => favStore.syncStatus(r.id))
    }
  } catch (e: any) {
    ElMessage.error('加载资源失败：' + (e.message || ''))
    resources.value = []
  } finally {
    loading.value = false
  }
}

function selectCategory(node?: any) {
  query.categoryId = node?.id
  query.page = 1
  loadResources()
}

let searchTimer: any
function onSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    query.page = 1
    loadResources()
  }, 420)
}

function goPublish() {
  router.push('/publish')
}

// 初始化
onMounted(async () => {
  await categoryStore.loadTree()

  // 从 URL 恢复筛选
  if (route.query.categoryId) query.categoryId = Number(route.query.categoryId)
  if (route.query.keyword) query.keyword = route.query.keyword as string
  if (route.query.page) query.page = Number(route.query.page)

  await loadResources()

  // 预加载收藏
  const auth = (await import('../stores/auth')).useAuthStore()
  if (auth.isAuthenticated) {
    favStore.hydrateMyFavorites()
  }
})

// 监听路由变化（从首页搜索跳转）
watch(() => route.query, (q) => {
  if (q.keyword && q.keyword !== query.keyword) {
    query.keyword = q.keyword as string
    query.page = 1
    loadResources()
  }
}, { immediate: false })
</script>