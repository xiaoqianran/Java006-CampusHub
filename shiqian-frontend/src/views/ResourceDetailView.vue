<template>
  <div class="max-w-5xl mx-auto px-6 py-10">
    <!-- 返回 -->
    <router-link to="/resources" class="text-sm text-[#0f766e] hover:underline mb-6 inline-flex items-center gap-1">
      ← 返回资源广场
    </router-link>

    <div v-if="loading" class="shiqian-card h-[420px] animate-pulse" />
    
    <template v-else-if="resource">
      <!-- 头部 -->
      <div class="flex flex-col md:flex-row md:items-end md:justify-between gap-4 mb-6">
        <div>
          <div class="flex items-center gap-3">
            <span class="status-badge" :class="`status-${resource.status}`">
              {{ statusLabel }}
            </span>
            <span class="text-xs text-[#8a7155]">v{{ resource.version }} · {{ categoryName }}</span>
          </div>
          <h1 class="text-4xl md:text-5xl font-semibold tracking-[-1.2px] leading-tight mt-2">{{ resource.title }}</h1>
        </div>

        <div class="flex items-center gap-3 shrink-0">
          <!-- 收藏 -->
          <button
            @click="toggleFav"
            :disabled="favLoading"
            class="flex items-center gap-2 px-5 h-11 rounded-2xl border border-[#e5e0d8] hover:bg-white transition-all active:scale-[0.985]"
            :class="{ 'text-rose-600 border-rose-200': isFav }"
          >
            <Heart :size="18" :fill="isFav ? 'currentColor' : 'none'" />
            <span class="text-sm font-medium">{{ isFav ? '已收藏' : '收藏此资源' }}</span>
          </button>

          <!-- 下载主按钮 -->
          <button
            @click="handleDownload"
            class="flex items-center gap-3 px-8 h-11 rounded-2xl bg-[#0f766e] hover:bg-[#0c5f57] text-white font-semibold shadow-sm active:scale-[0.985] transition-all"
          >
            <Download :size="18" />
            <span>立即下载</span>
            <span class="font-mono text-xs bg-white/20 px-2 py-px rounded"> {{ resource.downloadCount || 0 }} </span>
          </button>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <!-- 左侧主信息 -->
        <div class="lg:col-span-8">
          <div class="shiqian-card p-7">
            <div class="text-[#5c4630] whitespace-pre-wrap leading-relaxed text-[15px]">
              {{ resource.description || '这位同学没有留下详细描述。' }}
            </div>

            <div class="mt-8 pt-6 border-t text-xs text-[#8a7155] flex flex-wrap gap-x-6 gap-y-1">
              <div>文件大小：{{ formatSize(resource.fileSize) }}</div>
              <div>文件类型：{{ resource.fileType }}</div>
              <div>上传时间：{{ formatTime(resource.createTime) }}</div>
              <div>贡献者 ID：#{{ resource.userId }}</div>
            </div>
          </div>

          <!-- 相关资源 -->
          <div class="mt-8">
            <div class="font-medium mb-3 px-1 text-sm text-[#0f766e]">同分类下的其他资源</div>
            <div v-if="related.length" class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <ResourceCard v-for="r in related" :key="r.id" :resource="r" />
            </div>
            <div v-else class="text-xs text-[#8a7155] px-1">暂无其他资源</div>
          </div>
        </div>

        <!-- 右侧信息卡 -->
        <div class="lg:col-span-4 space-y-4">
          <div class="shiqian-card p-5 text-sm">
            <div class="font-medium mb-3">文件信息</div>
            <div class="space-y-2 text-[#5c4630]">
              <div class="flex justify-between"><span>下载次数</span> <span class="font-mono">{{ resource.downloadCount || 0 }}</span></div>
              <div class="flex justify-between"><span>当前版本</span> <span>v{{ resource.version }}</span></div>
              <div class="flex justify-between"><span>分类</span> <span>{{ categoryName }}</span></div>
            </div>
          </div>

          <div class="shiqian-card p-5 text-sm">
            <div class="font-medium mb-2">下载说明</div>
            <div class="text-xs text-[#5c4630] leading-relaxed">
              点击「立即下载」会通过 RabbitMQ 异步记录下载次数（已修复序列化问题）。<br>
              文件将直接从对象存储打开。
            </div>
          </div>

          <div v-if="resource.status === 0" class="p-4 rounded-2xl bg-amber-50 text-amber-800 text-xs border border-amber-100">
            该资源正在审核中，仅上传者与管理员可见。
          </div>
        </div>
      </div>
    </template>

    <div v-else class="text-center py-20 text-[#5c4630]">资源不存在或已被删除</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { Heart, Download } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { resourceApi } from '../api/resource'
import { useCategoryStore } from '../stores/category'
import { useFavoritesStore } from '../stores/favorites'
import type { ResourceItem } from '../types/resource'
import ResourceCard from '../components/ResourceCard.vue'

const route = useRoute()
const categoryStore = useCategoryStore()
const favStore = useFavoritesStore()

const resource = ref<ResourceItem | null>(null)
const related = ref<ResourceItem[]>([])
const loading = ref(true)

const isFav = computed(() => favStore.isFavorited(Number(route.params.id)))
const favLoading = computed(() => favStore.loadingIds.has(Number(route.params.id)))

const categoryName = computed(() => 
  categoryStore.getCategoryName(resource.value?.categoryId)
)

const statusLabel = computed(() => {
  const s = resource.value?.status
  if (s === 0) return '待审核'
  if (s === 2) return '审核未通过'
  return '已公开'
})

function formatSize(size?: number) {
  if (!size) return '未知'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatTime(t?: string) {
  if (!t) return '-'
  return new Date(t).toLocaleDateString('zh-CN')
}

async function loadDetail() {
  loading.value = true
  const id = Number(route.params.id)
  try {
    const [res, tree] = await Promise.all([
      resourceApi.getResource(id),
      categoryStore.loadTree()
    ])
    resource.value = res
    favStore.syncStatus(id) // 同步收藏状态

    // 加载同分类相关资源（简单实现）
    if (res?.categoryId) {
      const list = await resourceApi.pageResources({ page: 1, size: 6, categoryId: res.categoryId })
      related.value = (list.records || []).filter(r => r.id !== id && r.status === 1).slice(0, 4)
    }
  } catch (e: any) {
    ElMessage.error('加载详情失败')
    resource.value = null
  } finally {
    loading.value = false
  }
}

async function toggleFav() {
  if (!resource.value) return
  const ok = await favStore.toggleFavorite(resource.value.id)
  if (ok) {
    favStore.cacheDetail(resource.value)
  }
}

async function handleDownload() {
  if (!resource.value) return
  const id = resource.value.id
  try {
    await resourceApi.downloadResource(id)
    // 乐观 +1
    resource.value.downloadCount = (resource.value.downloadCount || 0) + 1
    ElMessage.success('已记录下载（MQ 异步统计）')
    window.open(resource.value.fileUrl, '_blank')
  } catch (e: any) {
    ElMessage.error(e.message || '下载失败')
    window.open(resource.value.fileUrl, '_blank')
  }
}

onMounted(loadDetail)
</script>