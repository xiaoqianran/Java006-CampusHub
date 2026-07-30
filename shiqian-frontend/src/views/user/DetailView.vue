<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Star, StarFilled, Download, View, User, CopyDocument } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import MarkdownPreview from '@/components/MarkdownPreview.vue'
import AttachmentPreviewDialog from '@/components/AttachmentPreviewDialog.vue'
import { contentSceneLabel, useAppStore, type ResourceAttachmentItem } from '@/stores/app'
import { buildApiUrl } from '@/api/client'

const route = useRoute()
const router = useRouter()
const store = useAppStore()
const resource = computed(() => store.getResource(Number(route.params.id)))
const related = computed(() => {
  if (!resource.value) return []
  const others = store.publishedResources.filter(item => item.id !== resource.value!.id)
  const sameScene = others.filter(item => item.scene === resource.value!.scene)
  const otherScene = others.filter(item => item.scene !== resource.value!.scene)
  const currentAuthor = resource.value!.author
  // Client-side popularity score mixing views + downloads (reuse loaded resources, no backend call)
  // + small same-author boost for smarter recs (surfaces co-authored content without breaking mix)
  const score = (item: any) => {
    let s = (item.downloads || 0) * 2 + (item.views || 0)
    if (item.author === currentAuthor) s += 25
    return s
  }
  sameScene.sort((a, b) => score(b) - score(a))
  otherScene.sort((a, b) => score(b) - score(a))
  return [...sameScene.slice(0, 2), ...otherScene.slice(0, 2)].slice(0, 4)
})
function isDisplayImage(att: { fileUrl?: string, fileName?: string, assetKind?: string }) {
  if (!att?.fileUrl) return false
  if (att.assetKind === 'IMAGE') return true
  if (/^https?:\/\//i.test(att.fileUrl)) return true
  return /\.(png|jpe?g|gif|webp|avif|bmp)(\?|$)/i.test(`${att.fileUrl} ${att.fileName || ''}`)
}

const imageAttachments = computed(() => (resource.value?.attachments || []).filter(isDisplayImage))
/** 加载失败的图链 id/url，静默隐藏，不占位 */
const brokenImageKeys = ref<Set<string>>(new Set())
const visibleImageAttachments = computed(() =>
  imageAttachments.value.filter(att => {
    const key = String(att.id || att.fileUrl)
    return !brokenImageKeys.value.has(key)
  })
)
const isGallery = computed(() => resource.value?.scene === 'GALLERY')
const promptText = computed(() =>
  (resource.value?.contentMarkdown || resource.value?.desc || '').trim()
)
// 画廊：摘要若与提示词实质相同则不展示，避免标题/摘要/正文三连重复
const showSummary = computed(() => {
  if (!resource.value) return false
  const summary = (resource.value.summary || resource.value.desc || '').trim()
  if (!summary) return false
  if (!isGallery.value) return true
  const prompt = promptText.value
  if (!prompt) return true
  return summary !== prompt && !prompt.startsWith(summary) && !summary.startsWith(prompt.slice(0, 40))
})
const nonImageAttachments = computed(() => resource.value?.attachments?.filter(att =>
  !(att.assetKind === 'IMAGE' || /\.(png|jpe?g|gif|webp)$/i.test(att.fileName || ''))
) || [])

const detailLoading = ref(true)
const previewVisible = ref(false)
const previewAttachment = ref<ResourceAttachmentItem | null>(null)

watch(
  () => resource.value?.id,
  () => {
    brokenImageKeys.value = new Set()
  }
)

onMounted(async () => {
  detailLoading.value = true
  try {
    await store.loadResourceDetail(Number(route.params.id))
    // 加载详情后立即记录一次浏览（支持未登录用户），乐观+1本地 views
    store.incrementView(Number(route.params.id))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资源加载失败')
  } finally {
    detailLoading.value = false
  }
})

async function toggleFavorite() {
  if (!store.logged) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  try {
    if (!resource.value) return
    await store.toggleFavorite(resource.value.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '收藏操作失败')
  }
}

async function download() {
  try {
    if (!resource.value) return
    const vo = await store.downloadResource(resource.value.id)
    // Prefer returned VO for latest primary (attachments already in resource)
    const fileUrl = (vo && vo.fileUrl) || primaryDownloadUrl()
    if (!fileUrl) {
      ElMessage.warning('该资源暂无可下载文件')
      return
    }
    window.open(buildApiUrl(fileUrl), '_blank')
    ElMessage.success('开始下载')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '下载失败')
  }
}

function primaryDownloadUrl() {
  return resource.value?.fileUrl || resource.value?.attachments?.find(att => att?.fileUrl)?.fileUrl || ''
}

function downloadAttachment(att: any) {
  if (att?.fileUrl) {
    window.open(buildApiUrl(att.fileUrl), '_blank')
  }
}

function previewFile(att: ResourceAttachmentItem) {
  previewAttachment.value = att
  previewVisible.value = true
}

function formatFileSize(size: number) {
  if (!size) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let s = size
  while (s >= 1024 && i < units.length - 1) {
    s /= 1024
    i++
  }
  return `${s.toFixed(1)} ${units[i]}`
}

function goBack() {
  if (window.history.state?.back) {
    router.back()
    return
  }
  const path = resource.value?.scene === 'BLOG'
    ? '/blog'
    : resource.value?.scene === 'GALLERY' ? '/images' : '/share'
  router.push(path)
}

async function copyPrompt() {
  if (!promptText.value) {
    ElMessage.warning('暂无提示词')
    return
  }
  try {
    await navigator.clipboard.writeText(promptText.value)
    ElMessage.success('提示词已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择')
  }
}

function hideBrokenDetailImage(att: ResourceAttachmentItem) {
  const key = String(att.id || att.fileUrl)
  if (!key || brokenImageKeys.value.has(key)) return
  const next = new Set(brokenImageKeys.value)
  next.add(key)
  brokenImageKeys.value = next
}
</script>

<template>
  <div v-loading="detailLoading" style="min-height: 480px;">
    <el-button text :icon="ArrowLeft" class="detail-back" @click="goBack">返回上一页</el-button>
    <section v-if="resource" class="detail-layout">
    <el-card class="detail-card" shadow="never">
      <el-tag>{{ contentSceneLabel(resource.scene) }}</el-tag>
      <el-tag v-if="resource.categoryId" type="info" effect="plain" style="margin-left: 6px">{{ resource.cat }}</el-tag>
      <el-tag v-if="(resource.downloads + resource.views) > 15" type="danger" size="small" effect="light" style="margin-left: 6px">受欢迎</el-tag>
      <h1 class="detail-title">{{ resource.title }}</h1>

      <p v-if="showSummary" class="sub detail-summary">
        {{ resource.summary || resource.desc }}
      </p>
      <div v-if="resource.tags" class="detail-tags">
        <el-tag
          v-for="tag in resource.tags.split(/[,，]/).map(t => t.trim()).filter(Boolean)"
          :key="tag"
          size="small"
          effect="plain"
        ># {{ tag }}</el-tag>
      </div>

      <div class="resource-meta">
        <el-tag size="small" type="info" effect="plain" style="font-size:13px; padding: 0 6px; height:20px; line-height:20px; vertical-align: middle;">
          <el-icon style="margin-right:3px; font-size:13px;"><User /></el-icon>作者：{{ resource.author }}
        </el-tag>
        <span><el-icon><View /></el-icon> 浏览 {{ resource.views }}</span>
        <span><el-icon><Download /></el-icon> 下载 {{ resource.downloads }}</span>
        <span>收藏 {{ resource.favs }}</span>
      </div>

      <div class="detail-actions">
        <el-button v-if="primaryDownloadUrl()" type="primary" :icon="Download" @click="download">
          下载{{ resource.attachments && resource.attachments.length ? '主文件' : '资源' }}
        </el-button>
        <el-button v-if="isGallery && promptText" type="primary" plain :icon="CopyDocument" @click="copyPrompt">
          复制提示词
        </el-button>
        <el-button :icon="store.isFavorite(resource.id) ? StarFilled : Star" @click="toggleFavorite">
          {{ store.isFavorite(resource.id) ? '取消收藏' : '加入收藏' }}
        </el-button>
      </div>

      <!-- 图片频道：有可加载的图才显示；链失效静默不渲染 -->
      <section v-if="isGallery && visibleImageAttachments.length" class="image-gallery">
        <img
          v-for="image in visibleImageAttachments"
          :key="image.id || image.fileUrl"
          :src="buildApiUrl(image.fileUrl, { inline: true })"
          :alt="image.fileName"
          @error="hideBrokenDetailImage(image)"
          @click="previewFile(image)"
        />
      </section>

      <template v-if="isGallery">
        <div class="prompt-header">
          <h2>提示词</h2>
          <el-button v-if="promptText" size="small" text type="primary" :icon="CopyDocument" @click="copyPrompt">
            一键复制
          </el-button>
        </div>
        <div v-if="promptText" class="prompt-box">
          <pre>{{ promptText }}</pre>
        </div>
        <div v-else class="empty-content">暂无提示词</div>

        <div v-if="nonImageAttachments.length" class="attachment-section">
          <h2>其他附件</h2>
          <div
            v-for="att in nonImageAttachments"
            :key="att.id || att.fileUrl"
            class="attachment-item"
            @click="previewFile(att)"
          >
            <span class="file-name">{{ att.fileName }}</span>
            <span class="file-meta">{{ att.fileType }} · {{ formatFileSize(att.fileSize) }}</span>
            <el-button size="small" @click.stop="previewFile(att)">预览</el-button>
            <el-button size="small" type="primary" @click.stop="downloadAttachment(att)">下载</el-button>
          </div>
        </div>
      </template>

      <template v-else>
        <h2>正文</h2>
        <div v-if="resource.contentMarkdown" class="markdown-section">
          <MarkdownPreview :model-value="resource.contentMarkdown" />
        </div>
        <div v-else-if="resource.desc" class="markdown-section fallback">
          <pre class="legacy-desc">{{ resource.desc }}</pre>
        </div>
        <div v-else class="empty-content">
          暂无正文内容
        </div>

        <div v-if="resource.attachments && resource.attachments.length > 0" class="attachment-section">
          <h2>附件</h2>
          <div v-for="att in resource.attachments" :key="att.id || att.fileUrl" class="attachment-item" @click="previewFile(att)">
            <span class="file-name">{{ att.fileName }}</span>
            <span class="file-meta">{{ att.fileType }} · {{ formatFileSize(att.fileSize) }}</span>
            <el-button size="small" @click.stop="previewFile(att)">预览</el-button>
            <el-button size="small" type="primary" @click.stop="downloadAttachment(att)">下载</el-button>
          </div>
        </div>
        <div v-else class="attachment-section">
          <h2>附件</h2>
          <p class="sub">该资源暂无附件</p>
        </div>
      </template>

      <!-- 旧的占位说明已移除，真实内容由 Markdown 渲染 -->

      <h2 style="margin-top: 32px;">评论区（示例）</h2>
      <div class="comment"><b>张同学</b><p class="sub">资料很完整，实验步骤可以直接对照学习。</p></div>
      <div class="comment"><b>管理员</b><p class="sub">已通过基础内容检查，下载前请注意课程版本差异。</p></div>
    </el-card>
    <aside>
      <el-card shadow="never">
        <h3>资源状态</h3>
        <StatusTag :status="resource.status" />
        <h3>相关推荐</h3>
        <div v-if="related.length">
          <p v-for="item in related" :key="item.id" style="margin: 0 0 6px 0; font-size: 13px;">
            <a @click="router.push(`/detail/${item.id}`)"><b>{{ item.title }}</b></a>
            <span class="sub" style="margin-left: 4px;">{{ item.type }}</span>
            <el-tag v-if="item.author === resource?.author" type="warning" size="small" effect="plain" style="margin-left: 4px; vertical-align: middle;">同作者</el-tag>
            <el-tag v-else-if="item.scene === resource?.scene" type="success" size="small" effect="plain" style="margin-left: 4px; vertical-align: middle;">同频道</el-tag>
            <el-tag v-else-if="((item.downloads || 0) + (item.views || 0)) > 10" type="info" size="small" effect="plain" style="margin-left: 4px; vertical-align: middle;">热门</el-tag>
          </p>
        </div>
        <p v-else class="sub">暂无相关资源</p>
      </el-card>
    </aside>
  </section>
  <el-empty v-else-if="!detailLoading" description="资源不存在" />
  <AttachmentPreviewDialog
    v-model="previewVisible"
    :attachment="previewAttachment"
    @download="downloadAttachment"
  />
  </div>
</template>

<style scoped>
.detail-back {
  margin-bottom: 14px;
}

.detail-summary {
  font-size: 15px;
  color: var(--text-secondary, #4b5563);
  margin-bottom: 8px;
  line-height: 1.6;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 14px;
}

.detail-actions {
  margin: 20px 0 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.image-gallery {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 18px 0 8px;
}

.image-gallery img {
  width: 100%;
  max-height: 640px;
  object-fit: cover;
  border: 1px solid var(--line);
  border-radius: 14px;
  cursor: zoom-in;
  background: #0f172a;
}

.image-gallery img:first-child:last-child {
  grid-column: 1 / -1;
  object-fit: contain;
  max-height: min(72vh, 760px);
}

.prompt-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 20px;
}

.prompt-header h2 {
  margin: 0;
}

.prompt-box {
  margin-top: 10px;
  padding: 16px 18px;
  border-radius: 12px;
  border: 1px solid var(--line);
  background: var(--bg-card, #f8fafc);
}

.prompt-box pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-sans-serif, system-ui, -apple-system, sans-serif;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary, #111827);
}

[data-theme="dark"] .prompt-box {
  background: #111827;
  border-color: #334155;
}

[data-theme="dark"] .prompt-box pre {
  color: #e2e8f0;
}

.markdown-section {
  margin-top: 12px;
}

.legacy-desc {
  white-space: pre-wrap;
  background: var(--bg-card, #f8f9fa);
  padding: 16px;
  border-radius: 8px;
  border: 1px solid var(--line, #e5e7eb);
  font-family: ui-monospace, monospace;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-primary, #111827);
}

[data-theme="dark"] .legacy-desc {
  background: #1f2937;
  border-color: #374151;
  color: #e5e7eb;
}

.empty-content {
  color: #6b7280;
  font-size: 14px;
  padding: 24px 0;
  text-align: center;
  border: 1px dashed var(--line, #e5e7eb);
  border-radius: 8px;
}

.attachment-section {
  margin-top: 24px;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: var(--bg-subtle, #f8f9fa);
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
}

[data-theme="dark"] .attachment-item {
  background: #1f2937;
}

@media (max-width: 720px) {
  .image-gallery {
    grid-template-columns: 1fr;
  }
}

.attachment-item .file-name {
  font-weight: 500;
  flex: 1;
}

.attachment-item .file-meta {
  color: var(--text-secondary, #6b7280);
  font-size: 13px;
}

/* 相关推荐 sidebar 视觉优化（紧凑间距 + 小标签更好呈现） */
aside p { margin-bottom: 4px; line-height: 1.3; }
aside .el-tag { font-size: 10px; padding: 0 3px; height: 15px; line-height: 15px; border-radius: 3px; }
</style>
