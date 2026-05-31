<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Upload, Star } from '@element-plus/icons-vue'
import ResourceCard from '@/components/ResourceCard.vue'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const store = useAppStore()
const icons = ['💻', '∑', 'A', '🎓']

onMounted(() => {
  store.loadHomeData().catch(() => undefined)
})

function search() {
  store.searchResources().catch(() => undefined)
  router.push('/plaza')
}

function openCategory(category: string) {
  store.setCategory(category)
  router.push('/plaza')
}
</script>

<template>
  <section class="hero">
    <h1>让校园资料流动起来</h1>
    <p>把课程笔记、实验报告、复习资料和项目模板集中到一个统一入口。前台浏览、发布、收藏，后台审核、分类、用户管理共用同一套逻辑。</p>
    <el-input v-model="store.keyword" class="hero-search" size="large" placeholder="搜索课程、资料、真题、项目模板" :prefix-icon="Search" @keyup.enter="search">
      <template #append>
        <el-button type="primary" @click="search">搜索资源</el-button>
      </template>
    </el-input>
  </section>

  <section class="section">
    <div class="page-title">
      <div>
        <h1>热门分类</h1>
        <p class="sub">分类点击后进入资源广场并自动筛选。</p>
      </div>
      <el-button text type="primary" @click="router.push('/categories')">查看全部</el-button>
    </div>
    <div class="category-grid">
      <el-card v-for="(category, index) in store.categories.slice(0, 4)" :key="category" class="category-card" shadow="never" @click="openCategory(category)">
        <span class="category-icon">{{ icons[index] }}</span>
        <span><b>{{ category }}</b><br><span class="sub">{{ store.resources.filter(item => item.cat === category).length }} 个资源</span></span>
      </el-card>
    </div>
  </section>

  <section class="section">
    <div class="page-title">
      <div>
        <h1>最新资源</h1>
        <p class="sub">只展示审核通过的资源。</p>
      </div>
      <el-button type="primary" :icon="Upload" @click="router.push('/publish')">发布资源</el-button>
    </div>
    <div class="resource-grid">
      <!-- ResourceCard 依赖 store.author（后端富化真实 authorNickname） -->
      <ResourceCard v-for="item in store.publishedResources.slice(0, 3)" :key="item.id" :item="item" />
    </div>
  </section>

  <section class="section stat-grid">
    <div class="stat-card"><b>{{ store.resources.length }}</b><span class="sub">演示资源</span></div>
    <div class="stat-card"><b>{{ store.favoriteIds.length }}</b><span class="sub"><el-icon><Star /></el-icon> 我的收藏</span></div>
    <div class="stat-card"><b>{{ store.categories.length }}</b><span class="sub">核心分类</span></div>
    <div class="stat-card"><b>5</b><span class="sub">后台管理模块</span></div>
  </section>
</template>
