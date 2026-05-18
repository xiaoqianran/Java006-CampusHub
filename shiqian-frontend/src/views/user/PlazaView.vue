<script setup lang="ts">
import { onMounted, watch } from 'vue'
import ResourceCard from '@/components/ResourceCard.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()

onMounted(() => {
  store.loadHomeData().catch(() => undefined)
})

watch(() => [store.activeCategory, store.keyword], () => {
  store.searchResources().catch(() => undefined)
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
      <el-button @click="store.resetFilters()">重置</el-button>
    </div>

    <div v-if="store.filteredResources.length" class="resource-grid">
      <ResourceCard v-for="item in store.filteredResources" :key="item.id" :item="item" />
    </div>
    <el-empty v-else description="暂无匹配资源" />
  </section>
</template>
