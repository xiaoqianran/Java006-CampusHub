<script setup lang="ts">
import { onMounted, watch } from 'vue'
import ResourceCard from '@/components/ResourceCard.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()

onMounted(() => {
  store.loadHomeData().catch(() => undefined)
})

watch(() => [store.activeCategory, store.keyword, store.sortMode], () => {
  store.searchResources({ sort: store.sortMode }).catch(() => undefined)
})
</script>

<template>
  <section>
    <div class="page-title">
      <div>
        <h1>资源广场</h1>
        <p class="sub">统一承接首页搜索、分类点击、详情页相关推荐。</p>
      </div>
      <el-button type="primary" @click="$router.push('/publish')">发布资源</el-button>
    </div>

    <div class="toolbar">
      <el-input v-model="store.keyword" clearable placeholder="搜索资源" style="max-width: 420px" />
      <el-select v-model="store.activeCategory" style="max-width: 180px">
        <el-option label="全部分类" value="全部分类" />
        <el-option v-for="category in store.categories" :key="category" :label="category" :value="category" />
      </el-select>
      <el-select v-model="store.sortMode" style="max-width: 120px">
        <el-option label="最新" value="newest" />
        <el-option label="最热" value="hottest" />
      </el-select>
      <el-button @click="store.resetFilters()">重置</el-button>
    </div>

    <div v-if="store.filteredResources.length" class="resource-grid">
      <!-- 使用 store mapResource + 后端 authorNickname 展示真实作者（不再是“用户 {id}”） -->
      <ResourceCard v-for="item in store.filteredResources" :key="item.id" :item="item" />
    </div>
    <el-empty v-else description="暂无匹配资源" />
  </section>
</template>
