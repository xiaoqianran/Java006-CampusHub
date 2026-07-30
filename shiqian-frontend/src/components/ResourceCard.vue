<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { StarFilled, Star, View, Download, User } from '@element-plus/icons-vue'
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
      <!-- Tags group keeps right-aligned together under space-between flex (minimal addition) -->
      <div style="display:flex; gap:4px; align-items:flex-end;">
        <el-tag effect="light">{{ item.type }}</el-tag>
        <el-tag v-if="(item.downloads || 0) + (item.views || 0) > 15" type="danger" size="small" effect="light">受欢迎</el-tag>
      </div>
    </div>
    <p class="resource-desc">{{ item.desc }}</p>
    <div class="resource-meta">
      <!-- 改进：使用 el-tag 徽章让作者信息更醒目、一致且视觉突出（badge 样式） -->
      <el-tag size="small" type="info" effect="plain" style="font-size:12px; padding: 0 6px; height:18px; line-height:18px;">
        <el-icon style="margin-right:2px; font-size:12px;"><User /></el-icon>{{ item.author }}
      </el-tag>
      <span><el-icon><View /></el-icon>{{ item.views }}</span>
      <span><el-icon><Download /></el-icon>{{ item.downloads }}</span>
      <span v-if="item.attachments && item.attachments.length">📎 {{ item.attachments.length }}</span>
    </div>
    <div class="card-footer-row">
      <el-button text type="primary" @click.stop="openDetail">查看详情</el-button>
      <el-button :icon="favorite ? StarFilled : Star" circle @click="toggleFavorite" />
    </div>
  </el-card>
</template>
