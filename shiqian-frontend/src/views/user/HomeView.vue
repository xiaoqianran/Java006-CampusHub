<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, ArrowRight } from '@element-plus/icons-vue'
import ResourceCard from '@/components/ResourceCard.vue'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const store = useAppStore()
const homeKeyword = ref('')
const resourceMode = ref<'newest' | 'hottest'>('newest')

const featuredResources = computed(() => {
  const resources = resourceMode.value === 'hottest'
    ? [...store.publishedResources].sort((a, b) =>
        ((b.downloads || 0) + (b.views || 0)) - ((a.downloads || 0) + (a.views || 0)) || b.id - a.id
      )
    : [...store.publishedResources].sort((a, b) => b.id - a.id)
  return resources.slice(0, 6)
})

onMounted(() => {
  store.loadHomeData().catch(() => undefined)
})

function search() {
  const keyword = homeKeyword.value.trim()
  router.push({
    path: '/resources',
    query: keyword ? { keyword } : {}
  })
}

function openCategory(category: string) {
  router.push({
    path: '/resources',
    query: { category }
  })
}

function viewAllResources() {
  router.push({
    path: '/resources',
    query: resourceMode.value === 'hottest' ? { sort: 'hottest' } : {}
  })
}
</script>

<template>
  <section class="hero">
    <h1>让校园资料流动起来</h1>
    <p>搜索课程笔记、实验报告、复习资料和项目模板，一个入口找到你需要的校园资源。</p>
    <el-input v-model="homeKeyword" class="hero-search" size="large" placeholder="搜索课程、资料、真题、项目模板" :prefix-icon="Search" @keyup.enter="search">
      <template #append>
        <el-button type="primary" @click="search">搜索资源</el-button>
      </template>
    </el-input>
  </section>

  <section class="section">
    <div class="page-title">
      <div>
        <h1>按分类查找</h1>
        <p class="sub">分类是资源中心的快捷筛选，不再跳转到独立页面。</p>
      </div>
      <el-button text type="primary" :icon="ArrowRight" @click="router.push('/resources')">全部资源</el-button>
    </div>
    <div v-loading="store.loading" class="category-shortcuts">
      <button
        v-for="category in store.categories.slice(0, 8)"
        :key="category"
        type="button"
        class="category-shortcut"
        @click="openCategory(category)"
      >
        <span>{{ category }}</span>
        <small>{{ store.publishedResources.filter(item => item.cat === category).length }} 个</small>
      </button>
    </div>
  </section>

  <section class="section">
    <div class="page-title resource-section-title">
      <div>
        <h1>资源推荐</h1>
        <p class="sub">在同一位置切换最新发布和热门内容。</p>
      </div>
      <el-radio-group v-model="resourceMode">
        <el-radio-button value="newest">最新发布</el-radio-button>
        <el-radio-button value="hottest">热门内容</el-radio-button>
      </el-radio-group>
    </div>
    <div v-loading="store.loading" class="resource-grid" style="min-height: 120px;">
      <ResourceCard v-for="item in featuredResources" :key="`${resourceMode}-${item.id}`" :item="item" />
    </div>
    <div class="section-more">
      <el-button type="primary" plain :icon="ArrowRight" @click="viewAllResources">查看全部资源</el-button>
    </div>
  </section>
</template>

<style scoped>
.category-shortcuts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  min-height: 70px;
}

.category-shortcut {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 58px;
  padding: 0 16px;
  color: var(--text);
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 14px;
  font: inherit;
  cursor: pointer;
  transition: border-color .18s, background-color .18s, transform .18s;
}

.category-shortcut:hover {
  color: var(--primary);
  border-color: var(--primary);
  background: var(--primary-soft);
  transform: translateY(-1px);
}

.category-shortcut small {
  color: var(--muted);
  white-space: nowrap;
}

.section-more {
  display: flex;
  justify-content: center;
  margin-top: 22px;
}

@media (max-width: 980px) {
  .category-shortcuts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .category-shortcuts {
    grid-template-columns: 1fr;
  }

  .resource-section-title {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
