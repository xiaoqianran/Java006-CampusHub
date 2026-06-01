<script setup lang="ts">
import { onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import ResourceCard from '@/components/ResourceCard.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()

onMounted(() => {
  if (!store.logged) return
  store.loadFavorites().catch(error => {
    ElMessage.error(error instanceof Error ? error.message : '收藏加载失败')
  })
})
</script>

<template>
  <section>
    <div class="page-title">
      <div>
        <h1>我的收藏</h1>
        <p class="sub">收藏按钮在广场和详情页保持同一状态。</p>
      </div>
      <el-select v-model="store.sortMode" style="width: 140px" size="small">
        <el-option label="最新" value="newest" />
        <el-option label="最热" value="hottest" />
      </el-select>
    </div>
    <div v-if="store.favoriteResources.length" class="resource-grid">
      <ResourceCard v-for="item in store.favoriteResources" :key="item.id" :item="item" />
    </div>
    <el-empty v-else description="暂无收藏" />
  </section>
</template>
