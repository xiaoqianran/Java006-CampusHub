<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import ResourceCard from '@/components/ResourceCard.vue'
import { useCatalogStore } from '@/stores/catalog'
import { useResourceStore } from '@/stores/resource'
import type { ContentSceneFilter } from '@/stores/types'

const catalog = useCatalogStore()
const resource = useResourceStore()
const route = useRoute()
const router = useRouter()
const ready = ref(false)
const currentPage = ref(1)
const selectedCategoryId = ref<number | undefined>()
const selectedTag = ref('')
const PAGE_SIZE = 24
let searchTimer: ReturnType<typeof setTimeout> | null = null

function queryValue(value: unknown) {
  return typeof value === 'string' ? value : ''
}

const currentScene = computed<ContentSceneFilter>(() => {
  const value = String(route.meta.scene || 'ALL')
  return ['BLOG', 'GALLERY', 'SHARE'].includes(value)
    ? value as ContentSceneFilter
    : 'ALL'
})

const pageInfo = computed(() => {
  if (currentScene.value === 'BLOG') {
    return { title: '博客', description: '阅读观点、教程、经验与校园故事。', search: '搜索博客和标签' }
  }
  if (currentScene.value === 'GALLERY') {
    return { title: '图片', description: '浏览作品、相册和视觉灵感。', search: '搜索图片帖和标签' }
  }
  if (currentScene.value === 'SHARE') {
    return { title: '资料', description: '发现课件、源码、文件和实用分享。', search: '搜索资料、文件和标签' }
  }
  return { title: '发现内容', description: '一起浏览博客、图片和资料。', search: '搜索全部内容和标签' }
})

function applyRouteFilters() {
  resource.keyword = queryValue(route.query.keyword)
  resource.activeScene = currentScene.value
  resource.sortMode = route.query.sort === 'hottest' ? 'hottest' : 'newest'
  const requestedPage = Number(queryValue(route.query.page))
  currentPage.value = Number.isInteger(requestedPage) && requestedPage > 0
    ? requestedPage
    : 1
  const requestedCategoryId = Number(queryValue(route.query.categoryId))
  selectedCategoryId.value = Number.isInteger(requestedCategoryId) && requestedCategoryId > 0
    ? requestedCategoryId
    : undefined
  selectedTag.value = queryValue(route.query.tag)
}

function filterQuery() {
  const query: Record<string, string> = {}
  const keyword = resource.keyword.trim()
  if (keyword) query.keyword = keyword
  if (resource.sortMode === 'hottest') query.sort = 'hottest'
  if (selectedCategoryId.value) query.categoryId = String(selectedCategoryId.value)
  if (selectedTag.value) query.tag = selectedTag.value
  if (currentPage.value > 1) query.page = String(currentPage.value)
  return query
}

async function runSearch() {
  try {
    await resource.searchResources({
      sort: resource.sortMode,
      scene: currentScene.value,
      page: currentPage.value,
      size: PAGE_SIZE,
      categoryId: selectedCategoryId.value,
      tag: selectedTag.value || undefined
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '内容加载失败')
  }
}

function scheduleSearch() {
  resource.cancelResourceSearch()
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    void runSearch()
  }, 300)
}

function resetFilters() {
  resource.keyword = ''
  resource.sortMode = 'newest'
  selectedCategoryId.value = undefined
  selectedTag.value = ''
  updateFilters()
}

function updateFilters() {
  currentPage.value = 1
  void router.replace({ path: route.path, query: filterQuery() })
  scheduleSearch()
}

function changePage(page: number) {
  currentPage.value = page
  void router.replace({ path: route.path, query: filterQuery() })
  scheduleSearch()
}

applyRouteFilters()

onMounted(async () => {
  try {
    ready.value = true
    await Promise.all([
      catalog.loadCategories(),
      catalog.loadTags()
    ])
    await runSearch()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '内容加载失败')
  }
})

watch(() => [route.path, route.query], () => {
  applyRouteFilters()
  if (ready.value) scheduleSearch()
}, { deep: true })

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer)
  resource.cancelResourceSearch()
  resource.activeScene = 'ALL'
})
</script>

<template>
  <section>
    <div class="page-title">
      <div>
        <h1>{{ pageInfo.title }}</h1>
        <p class="sub">{{ pageInfo.description }}</p>
      </div>
      <span class="result-count">共 {{ resource.searchResultTotal }} 个结果</span>
    </div>

    <div class="filter-panel">
      <div class="toolbar">
        <el-input
          v-model="resource.keyword"
          clearable
          :placeholder="pageInfo.search"
          class="resource-search"
          @input="updateFilters"
        />
        <el-select v-model="resource.sortMode" class="sort-select" @change="updateFilters">
          <el-option label="最新发布" value="newest" />
          <el-option label="热门优先" value="hottest" />
        </el-select>
        <el-select
          v-model="selectedCategoryId"
          clearable
          filterable
          class="taxonomy-select"
          placeholder="全部分类"
          @change="updateFilters"
        >
          <el-option
            v-for="category in catalog.flatCategories"
            :key="category.id"
            :label="category.name"
            :value="category.id"
          />
        </el-select>
        <el-select
          v-model="selectedTag"
          clearable
          filterable
          class="taxonomy-select"
          placeholder="全部标签"
          @change="updateFilters"
        >
          <el-option
            v-for="tag in catalog.tags"
            :key="tag.id"
            :label="`# ${tag.name}`"
            :value="tag.name"
          />
        </el-select>
        <el-button @click="resetFilters">重置筛选</el-button>
      </div>
    </div>

    <div v-loading="resource.searchLoading" class="resource-results">
      <div
        v-if="resource.filteredResources.length"
        class="resource-grid"
        :class="{ 'gallery-grid': currentScene === 'GALLERY' }"
      >
        <ResourceCard v-for="item in resource.filteredResources" :key="item.id" :item="item" />
      </div>
      <el-empty v-else-if="!resource.searchLoading" description="暂无匹配内容" />
    </div>
    <el-pagination
      v-if="resource.searchResultTotal > PAGE_SIZE"
      class="content-pagination"
      background
      layout="prev, pager, next"
      :current-page="currentPage"
      :page-size="PAGE_SIZE"
      :total="resource.searchResultTotal"
      @current-change="changePage"
    />
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

.taxonomy-select {
  width: 170px;
}

.resource-results {
  min-height: 160px;
}

.gallery-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.content-pagination {
  justify-content: center;
  margin-top: 26px;
}

@media (max-width: 1100px) {
  .gallery-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .gallery-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .resource-search,
  .sort-select {
    width: 100%;
    max-width: none;
  }

  .gallery-grid {
    grid-template-columns: 1fr;
  }
}
</style>
