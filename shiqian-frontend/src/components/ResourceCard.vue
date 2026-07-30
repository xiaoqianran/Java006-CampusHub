<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { StarFilled, Star, View, Download, User } from '@element-plus/icons-vue'
import { contentSceneLabel, type ResourceItem, useAppStore } from '@/stores/app'
import { buildApiUrl } from '@/api/client'

const props = defineProps<{ item: ResourceItem }>()
const store = useAppStore()
const router = useRouter()
const favorite = computed(() => store.isFavorite(props.item.id))
const coverImage = computed(() => {
  const attachment = props.item.attachments?.find(item =>
    item.assetKind === 'IMAGE' || /\.(png|jpe?g|gif|webp)$/i.test(item.fileName)
  )
  return attachment ? buildApiUrl(attachment.fileUrl, { inline: true }) : ''
})
const visibleTags = computed(() => (props.item.tags || '')
  .split(/[,，]/)
  .map(item => item.trim())
  .filter(Boolean)
  .slice(0, 3))

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
  <el-card class="resource-card" :class="{ 'gallery-card': item.scene === 'GALLERY' }" shadow="hover" @click="openDetail">
    <img v-if="coverImage" :src="coverImage" :alt="item.title" class="card-image" />
    <div class="resource-cover">
      <div>
        <span class="cover-category">{{ contentSceneLabel(item.scene) }}</span>
        <h3>{{ item.title }}</h3>
      </div>
      <!-- Tags group keeps right-aligned together under space-between flex (minimal addition) -->
      <div style="display:flex; gap:4px; align-items:flex-end;">
        <el-tag effect="light">{{ item.type }}</el-tag>
        <el-tag v-if="(item.downloads || 0) + (item.views || 0) > 15" type="danger" size="small" effect="light">受欢迎</el-tag>
      </div>
    </div>
    <p class="resource-desc">{{ item.desc }}</p>
    <div v-if="visibleTags.length" class="card-tags">
      <el-tag v-for="tag in visibleTags" :key="tag" size="small" effect="plain"># {{ tag }}</el-tag>
    </div>
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

<style scoped>
.card-image {
  width: calc(100% + 40px);
  height: 190px;
  margin: -20px -20px 16px;
  object-fit: cover;
  border-bottom: 1px solid var(--line);
}

.gallery-card .card-image {
  height: 250px;
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: -4px 0 12px;
}
</style>
