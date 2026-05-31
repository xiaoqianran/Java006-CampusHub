<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { Star, StarFilled, Download } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import MarkdownPreview from '@/components/MarkdownPreview.vue'
import { useAppStore } from '@/stores/app'
import { buildApiUrl } from '@/api/client'

const route = useRoute()
const router = useRouter()
const store = useAppStore()
const resource = computed(() => store.getResource(Number(route.params.id)))
const related = computed(() => resource.value
  ? store.resources.filter(item => item.cat === resource.value?.cat && item.id !== resource.value.id).slice(0, 3)
  : [])

onMounted(() => {
  store.loadResourceDetail(Number(route.params.id)).catch(error => {
    ElMessage.error(error instanceof Error ? error.message : '资源加载失败')
  })
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
    await store.downloadResource(resource.value.id)
    if (resource.value.fileUrl) window.open(buildApiUrl(resource.value.fileUrl), '_blank')
    ElMessage.success('下载请求已提交')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '下载失败')
  }
}

function downloadAttachment(att: any) {
  if (att?.fileUrl) {
    window.open(buildApiUrl(att.fileUrl), '_blank')
  }
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
</script>

<template>
  <section v-if="resource" class="detail-layout">
    <el-card class="detail-card" shadow="never">
      <el-tag>{{ resource.cat }}</el-tag>
      <h1 class="detail-title">{{ resource.title }}</h1>

      <!-- 摘要：优先 summary -->
      <p v-if="resource.summary || resource.desc" class="sub detail-summary">
        {{ resource.summary || resource.desc }}
      </p>

      <div class="resource-meta">
        <span>作者：{{ resource.author }}</span>
        <span>浏览 {{ resource.views }}</span>
        <span>下载 {{ resource.downloads }}</span>
        <span>收藏 {{ resource.favs }}</span>
      </div>

      <div style="margin: 24px 0; display: flex; gap: 12px">
        <el-button type="primary" :icon="Download" @click="download">下载资源</el-button>
        <el-button :icon="store.isFavorite(resource.id) ? StarFilled : Star" @click="toggleFavorite">
          {{ store.isFavorite(resource.id) ? '取消收藏' : '加入收藏' }}
        </el-button>
      </div>

      <!-- 正文：优先 contentMarkdown 的 Markdown 渲染 -->
      <h2>资源正文</h2>
      <div v-if="resource.contentMarkdown" class="markdown-section">
        <MarkdownPreview :model-value="resource.contentMarkdown" />
      </div>
      <div v-else-if="resource.desc" class="markdown-section fallback">
        <!-- 兼容旧数据：没有 contentMarkdown 时回退显示 desc（纯文本） -->
        <pre class="legacy-desc">{{ resource.desc }}</pre>
      </div>
      <div v-else class="empty-content">
        暂无正文内容
      </div>

      <!-- 第二阶段：附件列表 -->
      <div v-if="resource.attachments && resource.attachments.length > 0" class="attachment-section">
        <h2>附件</h2>
        <div v-for="att in resource.attachments" :key="att.id || att.fileUrl" class="attachment-item">
          <span class="file-name">{{ att.fileName }}</span>
          <span class="file-meta">{{ att.fileType }} · {{ formatFileSize(att.fileSize) }}</span>
          <el-button size="small" type="primary" @click="downloadAttachment(att)">下载</el-button>
        </div>
      </div>
      <div v-else class="attachment-section">
        <h2>附件</h2>
        <p class="sub">该资源暂无附件</p>
      </div>

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
          <p v-for="item in related" :key="item.id">
            <a @click="router.push(`/detail/${item.id}`)"><b>{{ item.title }}</b></a><br>
            <span class="sub">{{ item.type }}</span>
          </p>
        </div>
        <p v-else class="sub">暂无相关资源</p>
      </el-card>
    </aside>
  </section>
  <el-empty v-else description="资源加载中或不存在" />
</template>

<style scoped>
.detail-summary {
  font-size: 15px;
  color: var(--text-secondary, #4b5563);
  margin-bottom: 8px;
  line-height: 1.6;
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
}

[data-theme="dark"] .attachment-item {
  background: #1f2937;
}

.attachment-item .file-name {
  font-weight: 500;
  flex: 1;
}

.attachment-item .file-meta {
  color: var(--text-secondary, #6b7280);
  font-size: 13px;
}
</style>
