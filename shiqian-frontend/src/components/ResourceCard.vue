<template>
  <div 
    class="shiqian-card group flex flex-col h-full cursor-pointer overflow-hidden"
    @click="goDetail"
  >
    <!-- 文件类型视觉区 -->
    <div class="h-28 bg-gradient-to-br from-[#f8f5f0] to-white flex items-center justify-center relative border-b border-[#e5e0d8]">
      <div class="text-5xl opacity-80 select-none" :class="fileColorClass">
        {{ fileIcon }}
      </div>
      <div class="absolute top-3 right-3">
        <span class="file-badge">{{ resource.fileType?.split('/').pop() || 'FILE' }}</span>
      </div>
      <!-- 状态徽章（仅非公开时显示） -->
      <div v-if="resource.status !== 1" class="absolute top-3 left-3">
        <span class="status-badge" :class="`status-${resource.status}`">
          {{ statusText }}
        </span>
      </div>
    </div>

    <div class="p-4 flex flex-col flex-1">
      <!-- 标题 -->
      <div class="font-semibold text-base leading-snug line-clamp-2 group-hover:text-[#0f766e] transition-colors">
        {{ resource.title }}
      </div>

      <!-- 描述 -->
      <div class="text-sm text-[#5c4630] mt-2 line-clamp-3 flex-1">
        {{ resource.description || '这位同学没有留下描述...' }}
      </div>

      <!-- 元信息条 -->
      <div class="flex items-center justify-between text-xs text-[#8a7155] mt-4 pt-3 border-t border-[#f0e9dc]">
        <div class="flex items-center gap-1.5">
          <span>贡献者 #{{ resource.userId }}</span>
        </div>
        <div class="flex items-center gap-3">
          <span class="font-mono tabular-nums">{{ resource.downloadCount || 0 }} 下载</span>
          <span>v{{ resource.version || 1 }}</span>
        </div>
      </div>

      <!-- 操作区 -->
      <div class="flex items-center justify-between mt-3" @click.stop>
        <button
          @click="handleFavorite"
          :disabled="favLoading"
          class="fav-heart flex items-center gap-1 text-sm transition-colors"
          :class="{ 'active': isFav }"
        >
          <Heart :size="16" :fill="isFav ? '#e11d48' : 'none'" />
          <span class="text-xs">{{ isFav ? '已收藏' : '收藏' }}</span>
        </button>

        <button
          @click="handleDownload"
          class="text-xs px-3 py-1 rounded-xl bg-[#0f766e] text-white flex items-center gap-1 hover:bg-[#0c5f57] active:scale-[0.97] transition-all"
        >
          <Download :size="14" /> 下载
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Heart, Download } from 'lucide-vue-next'
import { useFavoritesStore } from '../stores/favorites'
import { ElMessage } from 'element-plus'
import type { ResourceItem } from '../types/resource'
import { resourceApi } from '../api/resource'

const props = defineProps<{
  resource: ResourceItem
}>()

const router = useRouter()
const favStore = useFavoritesStore()

const isFav = computed(() => favStore.isFavorited(props.resource.id))
const favLoading = computed(() => favStore.loadingIds.has(props.resource.id))

const fileIcon = computed(() => {
  const type = (props.resource.fileType || '').toLowerCase()
  if (type.includes('pdf')) return '📕'
  if (type.includes('ppt') || type.includes('powerpoint')) return '📊'
  if (type.includes('doc') || type.includes('word')) return '📄'
  if (type.includes('zip') || type.includes('rar')) return '📦'
  if (type.includes('image') || type.includes('png') || type.includes('jpg')) return '🖼️'
  if (type.includes('md') || type.includes('text')) return '📝'
  return '📁'
})

const fileColorClass = computed(() => {
  const t = (props.resource.fileType || '').toLowerCase()
  if (t.includes('pdf')) return 'text-red-600'
  if (t.includes('ppt')) return 'text-orange-600'
  if (t.includes('doc')) return 'text-blue-600'
  return 'text-[#0f766e]'
})

const statusText = computed(() => {
  if (props.resource.status === 0) return '待审核'
  if (props.resource.status === 2) return '已拒绝'
  return ''
})

function goDetail() {
  router.push(`/resources/${props.resource.id}`)
}

async function handleFavorite() {
  const success = await favStore.toggleFavorite(props.resource.id)
  if (success && !favStore.isFavorited(props.resource.id)) {
    // 如果刚刚取消，从详情缓存移除（可选）
  } else if (success) {
    favStore.cacheDetail(props.resource)
  }
}

async function handleDownload() {
  try {
    await resourceApi.downloadResource(props.resource.id)
    // 本地乐观 +1
    props.resource.downloadCount = (props.resource.downloadCount || 0) + 1
    ElMessage.success('下载请求已记录（异步统计）')
    // 真正打开文件
    window.open(props.resource.fileUrl, '_blank')
  } catch (e: any) {
    ElMessage.error(e.message || '下载记录失败')
    // 仍然尝试打开
    window.open(props.resource.fileUrl, '_blank')
  }
}
</script>