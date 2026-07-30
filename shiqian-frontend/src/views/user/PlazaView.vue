<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import ResourceCard from '@/components/ResourceCard.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const route = useRoute()
const router = useRouter()
const ready = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | null = null

function queryValue(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function applyRouteFilters() {
  store.keyword = queryValue(route.query.keyword)
  store.activeCategory = queryValue(route.query.category) || '全部分类'
  store.sortMode = route.query.sort === 'hottest' ? 'hottest' : 'newest'
}

function filterQuery() {
  const query: Record<string, string> = {}
  const keyword = store.keyword.trim()
  if (keyword) query.keyword = keyword
  if (store.activeCategory !== '全部分类') query.category = store.activeCategory
  if (store.sortMode === 'hottest') query.sort = 'hottest'
  return query
}

async function runSearch() {
  try {
    await store.searchResources({ sort: store.sortMode })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资源加载失败')
  }
}

function scheduleSearch() {
  store.cancelResourceSearch()
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    void runSearch()
  }, 300)
}

function resetFilters() {
  store.keyword = ''
  store.activeCategory = '全部分类'
  store.sortMode = 'newest'
}

applyRouteFilters()

onMounted(async () => {
  try {
    await store.loadHomeData()
    ready.value = true
    await runSearch()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资源中心加载失败')
  }
})

watch(() => route.query, () => {
  applyRouteFilters()
}, { deep: true })

watch(() => [store.activeCategory, store.keyword, store.sortMode], () => {
  if (!ready.value) return
  void router.replace({ path: '/resources', query: filterQuery() })
  scheduleSearch()
})

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer)
  store.cancelResourceSearch()
})
</script>

<template>
  <section>
    <div class="page-title">
      <div>
        <h1>资源中心</h1>
        <p class="sub">搜索、分类和排序集中在这里，查看详情后返回仍会保留当前筛选。</p>
      </div>
      <span class="result-count">共 {{ store.filteredResources.length }} 个结果</span>
    </div>

    <div class="filter-panel">
      <div class="toolbar">
        <el-input v-model="store.keyword" clearable placeholder="搜索标题、课程或资料内容" class="resource-search" />
        <el-select v-model="store.sortMode" class="sort-select">
          <el-option label="最新发布" value="newest" />
          <el-option label="热门优先" value="hottest" />
        </el-select>
        <el-button @click="resetFilters">重置筛选</el-button>
      </div>
      <div class="category-filters" aria-label="资源分类筛选">
        <button
          v-for="category in ['全部分类', ...store.categories]"
          :key="category"
          type="button"
          :class="{ active: store.activeCategory === category }"
          @click="store.activeCategory = category"
        >
          {{ category }}
        </button>
      </div>
    </div>

    <div v-loading="store.loading" class="resource-results">
      <div v-if="store.filteredResources.length" class="resource-grid">
        <ResourceCard v-for="item in store.filteredResources" :key="item.id" :item="item" />
      </div>
      <el-empty v-else-if="!store.loading" description="暂无匹配资源" />
    </div>
  </section>
</template>

<style scoped>
.result-count {
  color: var(--muted);
  font-size: 14px;
}

.filter-panel {
  margin-bottom: 22px;
  padding: 16px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 18px;
}

.toolbar {
  margin-bottom: 14px;
}

.resource-search {
  flex: 1;
  min-width: 260px;
  max-width: 560px;
}

.sort-select {
  width: 140px;
}

.category-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.category-filters button {
  padding: 7px 12px;
  color: var(--muted);
  background: transparent;
  border: 1px solid var(--line);
  border-radius: 999px;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: color .18s, border-color .18s, background-color .18s;
}

.category-filters button:hover,
.category-filters button.active {
  color: var(--primary);
  border-color: var(--primary);
  background: var(--primary-soft);
}

.resource-results {
  min-height: 160px;
}

@media (max-width: 560px) {
  .resource-search,
  .sort-select {
    width: 100%;
    max-width: none;
  }
}
</style>
