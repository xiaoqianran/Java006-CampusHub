<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { StarFilled, Star, View, Download } from '@element-plus/icons-vue'
import type { ResourceItem } from '@/stores/app'
import { useAppStore } from '@/stores/app'

const props = defineProps<{ item: ResourceItem }>()
const store = useAppStore()
const router = useRouter()
const favorite = computed(() => store.isFavorite(props.item.id))

function openDetail() {
  router.push(`/detail/${props.item.id}`)
}

async function toggleFavorite(event: MouseEvent) {
  event.stopPropagation()
  if (!store.logged) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  try {
    await store.toggleFavorite(props.item.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '收藏操作失败')
  }
}
</script>

<template>
  <el-card class="resource-card" shadow="hover" @click="openDetail">
    <div class="resource-cover">
      <div>
        <span class="cover-category">{{ item.cat }}</span>
        <h3>{{ item.title }}</h3>
      </div>
      <el-tag effect="light">{{ item.type }}</el-tag>
    </div>
    <p class="resource-desc">{{ item.desc }}</p>
    <div class="resource-meta">
      <span>作者：{{ item.author }}</span>
      <span><el-icon><View /></el-icon>{{ item.views }}</span>
      <span><el-icon><Download /></el-icon>{{ item.downloads }}</span>
    </div>
    <div class="card-footer-row">
      <el-button text type="primary" @click.stop="openDetail">查看详情</el-button>
      <el-button :icon="favorite ? StarFilled : Star" circle @click="toggleFavorite" />
    </div>
  </el-card>
</template>
