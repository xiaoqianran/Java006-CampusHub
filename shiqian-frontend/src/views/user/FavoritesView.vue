<script setup lang="ts">
import ResourceCard from '@/components/ResourceCard.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
</script>

<template>
  <section>
    <div class="page-title">
      <div>
        <h1>我的收藏</h1>
        <p class="sub">收藏按钮在广场和详情页保持同一状态。</p>
      </div>
    </div>
    <div v-if="store.favoriteResources.length" class="resource-grid">
      <ResourceCard v-for="item in store.favoriteResources" :key="item.id" :item="item" />
    </div>
    <el-empty v-else description="暂无收藏" />
  </section>
</template>
