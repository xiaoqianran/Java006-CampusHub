<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { StarFilled, Star, View, Download, User, CopyDocument } from '@element-plus/icons-vue'
import { contentSceneLabel, type ResourceItem, useAppStore } from '@/stores/app'
import { buildApiUrl } from '@/api/client'

const props = defineProps<{ item: ResourceItem }>()
const store = useAppStore()
const router = useRouter()
const favorite = computed(() => store.isFavorite(props.item.id))
const isGallery = computed(() => props.item.scene === 'GALLERY')
/** 图链失效时置 false，整块图区不渲染 */
const imageVisible = ref(true)

function isImageUrl(url?: string, fileName?: string) {
  const target = `${url || ''} ${fileName || ''}`
  if (!target.trim()) return false
  // 本地附件 / 常见后缀 / 即梦 CDN（后缀常在 query 前）
  if (/^https?:\/\//i.test(url || '')) return true
  return /\.(png|jpe?g|gif|webp|avif|bmp)(\?|$)/i.test(target)
}

const coverImage = computed(() => {
  const attachment = props.item.attachments?.find(item =>
    item.assetKind === 'IMAGE'
    || isImageUrl(item.fileUrl, item.fileName)
  )
  if (attachment?.fileUrl) {
    // https 直链直接展示，不下载、不改写
    return buildApiUrl(attachment.fileUrl, { inline: true })
  }
  if (props.item.fileUrl && isImageUrl(props.item.fileUrl)) {
    return buildApiUrl(props.item.fileUrl, { inline: true })
  }
  return ''
})

const showCover = computed(() => Boolean(coverImage.value) && imageVisible.value)

watch(
  () => [props.item.id, coverImage.value],
  () => {
    imageVisible.value = true
  }
)

const visibleTags = computed(() => (props.item.tags || '')
  .split(/[,，]/)
  .map(item => item.trim())
  .filter(Boolean)
  .filter(tag => !(isGallery.value && tag === '即梦'))
  .slice(0, 3))

const galleryMeta = computed(() => {
  if (!isGallery.value) return ''
  const text = (props.item.summary || props.item.desc || '').trim()
  if (!text || text === props.item.title) return ''
  return text
})

const promptForCopy = computed(() =>
  (props.item.contentMarkdown || props.item.title || '').trim()
)

function openDetail() {
  router.push(`/detail/${props.item.id}`)
}

function hideBrokenImage() {
  // 链接 404 / 过期 / 破图：直接不显示图，不留占位
  imageVisible.value = false
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

async function copyPrompt(event: MouseEvent) {
  event.stopPropagation()
  if (!promptForCopy.value) {
    ElMessage.warning('暂无提示词')
    return
  }
  try {
    await navigator.clipboard.writeText(promptForCopy.value)
    ElMessage.success('提示词已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择')
  }
}
</script>

<template>
  <el-card
    class="resource-card"
    :class="{
      'gallery-card': isGallery,
      'gallery-card--image': isGallery && showCover,
      'gallery-card--text': isGallery && !showCover
    }"
    shadow="hover"
    @click="openDetail"
  >
    <!-- 图片频道：有图才渲染图区；链失效则整块不显示 -->
    <template v-if="isGallery">
      <div v-if="showCover" class="gallery-media">
        <img
          :src="coverImage"
          :alt="item.title"
          class="card-image"
          loading="lazy"
          @error="hideBrokenImage"
        />
        <div class="gallery-overlay">
          <el-button
            v-if="promptForCopy"
            size="small"
            type="primary"
            :icon="CopyDocument"
            @click="copyPrompt"
          >
            复制提示词
          </el-button>
        </div>
      </div>
      <div class="gallery-body">
        <div class="gallery-top">
          <span class="cover-category">{{ contentSceneLabel(item.scene) }}</span>
          <el-tag v-if="(item.downloads || 0) + (item.views || 0) > 15" type="danger" size="small" effect="light">
            受欢迎
          </el-tag>
        </div>
        <h3 class="gallery-title" :title="item.title">{{ item.title }}</h3>
        <p v-if="galleryMeta" class="gallery-meta">{{ galleryMeta }}</p>
        <div v-if="visibleTags.length" class="card-tags">
          <el-tag v-for="tag in visibleTags" :key="tag" size="small" effect="plain"># {{ tag }}</el-tag>
        </div>
        <div class="resource-meta">
          <el-tag size="small" type="info" effect="plain" class="author-tag">
            <el-icon><User /></el-icon>{{ item.author }}
          </el-tag>
          <span><el-icon><View /></el-icon>{{ item.views }}</span>
          <span><el-icon><Download /></el-icon>{{ item.downloads }}</span>
        </div>
        <div class="card-footer-row">
          <el-button text type="primary" @click.stop="openDetail">查看</el-button>
          <el-button text type="primary" :icon="CopyDocument" @click="copyPrompt">复制提示词</el-button>
          <el-button :icon="favorite ? StarFilled : Star" circle @click="toggleFavorite" />
        </div>
      </div>
    </template>

    <!-- 博客 / 资料：有图才显示封面，破图静默隐藏 -->
    <template v-else>
      <img
        v-if="showCover"
        :src="coverImage"
        :alt="item.title"
        class="card-image"
        loading="lazy"
        @error="hideBrokenImage"
      />
      <div class="resource-cover">
        <div>
          <span class="cover-category">{{ contentSceneLabel(item.scene) }}</span>
          <h3>{{ item.title }}</h3>
        </div>
        <div class="cover-tags">
          <el-tag effect="light">{{ item.type }}</el-tag>
          <el-tag v-if="(item.downloads || 0) + (item.views || 0) > 15" type="danger" size="small" effect="light">
            受欢迎
          </el-tag>
        </div>
      </div>
      <p class="resource-desc">{{ item.desc }}</p>
      <div v-if="visibleTags.length" class="card-tags">
        <el-tag v-for="tag in visibleTags" :key="tag" size="small" effect="plain"># {{ tag }}</el-tag>
      </div>
      <div class="resource-meta">
        <el-tag size="small" type="info" effect="plain" class="author-tag">
          <el-icon><User /></el-icon>{{ item.author }}
        </el-tag>
        <span><el-icon><View /></el-icon>{{ item.views }}</span>
        <span><el-icon><Download /></el-icon>{{ item.downloads }}</span>
        <span v-if="item.attachments && item.attachments.length">📎 {{ item.attachments.length }}</span>
      </div>
      <div class="card-footer-row">
        <el-button text type="primary" @click.stop="openDetail">查看详情</el-button>
        <el-button :icon="favorite ? StarFilled : Star" circle @click="toggleFavorite" />
      </div>
    </template>
  </el-card>
</template>

<style scoped>
.card-image {
  width: calc(100% + 40px);
  height: 190px;
  margin: -20px -20px 16px;
  object-fit: cover;
  border-bottom: 1px solid var(--line);
  background: #0f172a;
}

.gallery-card {
  overflow: hidden;
}

.gallery-card.gallery-card--image :deep(.el-card__body) {
  padding: 0;
}

.gallery-media {
  position: relative;
  background: #0b1220;
}

.gallery-card--image .card-image {
  width: 100%;
  height: min(52vw, 320px);
  margin: 0;
  border-bottom: none;
  display: block;
}

.gallery-overlay {
  position: absolute;
  inset: auto 0 0 0;
  display: flex;
  justify-content: flex-end;
  padding: 12px;
  opacity: 0;
  transition: opacity 0.18s ease;
  background: linear-gradient(transparent, rgba(15, 23, 42, 0.72));
}

.gallery-media:hover .gallery-overlay,
.gallery-media:focus-within .gallery-overlay {
  opacity: 1;
}

.gallery-body {
  padding: 14px 16px 12px;
}

.gallery-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.gallery-title {
  margin: 0 0 6px;
  font-size: 15px;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.gallery-meta {
  margin: 0 0 8px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.cover-tags {
  display: flex;
  gap: 4px;
  align-items: flex-end;
}

.author-tag {
  font-size: 12px;
  padding: 0 6px;
  height: 18px;
  line-height: 18px;
}

.author-tag .el-icon {
  margin-right: 2px;
  font-size: 12px;
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: -4px 0 12px;
}

.gallery-card .card-tags {
  margin: 0 0 10px;
}

.gallery-card .resource-meta {
  margin-bottom: 4px;
}

.gallery-card .card-footer-row {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-wrap: wrap;
}
</style>
