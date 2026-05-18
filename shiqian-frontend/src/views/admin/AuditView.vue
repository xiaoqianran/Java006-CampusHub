<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import AdminLayout from '@/components/AdminLayout.vue'
import StatusTag from '@/components/StatusTag.vue'
import MarkdownPreview from '@/components/MarkdownPreview.vue'
import { useAppStore, type ResourceItem } from '@/stores/app'

const router = useRouter()
const store = useAppStore()

const detailVisible = ref(false)
const detailLoading = ref(false)
const openingId = ref<number | null>(null)
const current = ref<ResourceItem | null>(null)

onMounted(() => {
  store.loadHomeData().catch(() => undefined)
})

async function openDetail(row: ResourceItem) {
  current.value = row
  detailVisible.value = true
  detailLoading.value = true
  openingId.value = row.id

  try {
    await store.loadResourceDetail(row.id)
    current.value = store.getResource(row.id) || row
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资源详情加载失败')
  } finally {
    detailLoading.value = false
    openingId.value = null
  }
}

function openFile(row?: ResourceItem | null) {
  if (!row?.fileUrl) {
    ElMessage.warning('该资源没有可查看的文件地址')
    return
  }
  window.open(row.fileUrl, '_blank')
}

function openAttachment(att: any) {
  if (att?.fileUrl) {
    window.open(att.fileUrl, '_blank')
  }
}

function goDetail(row?: ResourceItem | null) {
  if (!row) return
  router.push(`/detail/${row.id}`)
}

async function approve(id: number) {
  try {
    await ElMessageBox.confirm('确认通过该资源审核吗？', '审核确认', { type: 'success' })
    await store.approveResource(id)
    ElMessage.success('已通过')
    detailVisible.value = false
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '审核失败')
    }
  }
}

async function reject(id: number) {
  try {
    await ElMessageBox.confirm('确认驳回该资源吗？', '驳回确认', { type: 'warning' })
    await store.rejectResource(id)
    ElMessage.warning('已驳回')
    detailVisible.value = false
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '审核失败')
    }
  }
}
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>资源审核</h1>
        <p class="sub">审核待发布资源，支持查看详情与原文件后再决定通过或驳回。</p>
      </div>
    </div>

    <el-table :data="store.pendingResources" class="panel audit-table" style="width: 100%">
      <el-table-column label="资源信息" min-width="320">
        <template #default="{ row }">
          <b>{{ row.title }}</b>
          <div class="sub audit-desc">{{ row.desc || '暂无简介' }}</div>
        </template>
      </el-table-column>

      <el-table-column prop="cat" label="分类" width="130" />
      <el-table-column prop="type" label="类型" width="140" />
      <el-table-column prop="author" label="发布者" width="130" />

      <el-table-column label="文件" width="120">
        <template #default="{ row }">
          <el-button v-if="row.fileUrl" size="small" text type="primary" @click="openFile(row)">
            查看文件
          </el-button>
          <span v-else class="sub">暂无文件</span>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <StatusTag :status="row.status" />
        </template>
      </el-table-column>

      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <div class="audit-actions">
            <!-- 第一层：审阅动作（最显眼） -->
            <el-button
              size="small"
              type="primary"
              class="audit-action-review"
              :loading="openingId === row.id"
              @click="openDetail(row)"
            >
              查看详情
            </el-button>

            <el-button
              size="small"
              class="audit-action-file"
              :disabled="!row.fileUrl"
              @click="openFile(row)"
            >
              打开文件
            </el-button>

            <span class="audit-action-divider"></span>

            <!-- 第二层：审核决策 -->
            <el-button
              size="small"
              class="audit-action-approve"
              @click="approve(row.id)"
            >
              通过
            </el-button>

            <el-button
              size="small"
              class="audit-action-reject"
              @click="reject(row.id)"
            >
              驳回
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!store.pendingResources.length" description="暂无待审核资源" />

    <el-dialog
      v-model="detailVisible"
      title="资源审核详情"
      width="720px"
      class="audit-detail-dialog"
      modal-class="audit-detail-modal"
      :destroy-on-close="false"
      :lock-scroll="false"
      :close-on-click-modal="false"
      @closed="current = null"
    >
      <div v-loading="detailLoading" class="audit-detail no-white-flash">
        <h2>{{ current?.title || '资源审核详情' }}</h2>

        <div class="audit-meta">
          <span>分类：{{ current?.cat || '-' }}</span>
          <span>类型：{{ current?.type || '-' }}</span>
          <span>发布者：{{ current?.author || '-' }}</span>
          <span>文件大小：{{ current?.fileSize || 0 }} 字节</span>
        </div>

        <!-- 摘要 -->
        <div class="audit-section">
          <h3>资源摘要</h3>
          <p class="audit-summary">{{ current?.summary || current?.desc || '暂无摘要' }}</p>
        </div>

        <!-- Markdown 正文 -->
        <div class="audit-section">
          <h3>资源正文</h3>
          <div v-if="current?.contentMarkdown" class="audit-markdown">
            <MarkdownPreview :model-value="current.contentMarkdown" />
          </div>
          <div v-else class="audit-fallback">
            <p class="sub">（兼容旧数据）{{ current?.desc || '暂无正文内容' }}</p>
          </div>
        </div>

        <!-- 第二阶段：附件列表 -->
        <div class="audit-section">
          <h3>附件</h3>
          <div v-if="current?.attachments && current.attachments.length > 0">
            <div v-for="att in current.attachments" :key="att.id || att.fileUrl" class="audit-attachment-item">
              <span>{{ att.fileName }}</span>
              <el-button size="small" type="primary" @click="openAttachment(att)">下载</el-button>
            </div>
          </div>
          <div v-else>
            <p class="sub">该资源暂无附件</p>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="audit-actions" style="width: 100%; justify-content: space-between;">
          <div>
            <el-button @click="detailVisible = false">关闭</el-button>
            <el-button :disabled="!current?.fileUrl" class="audit-action-file" @click="openFile(current)">打开原文件</el-button>
            <el-button v-if="current" class="audit-action-review" @click="goDetail(current)">跳转详情页</el-button>
          </div>

          <div>
            <el-button v-if="current" class="audit-action-reject" @click="reject(current.id)">驳回</el-button>
            <el-button v-if="current" class="audit-action-approve" @click="approve(current.id)">通过</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </AdminLayout>
</template>

<style scoped>
/* 审核详情弹窗深色模式适配 - 防止闪白 */
.audit-detail-dialog :deep(.el-dialog) {
  background: var(--bg-card, #ffffff);
}

[data-theme="dark"] .audit-detail-dialog :deep(.el-dialog) {
  background: var(--bg-card-dark, #1f2937);
  color: var(--text-primary-dark, #f3f4f6);
}

.audit-detail {
  padding: 8px 4px;
}

.audit-summary {
  font-size: 15px;
  color: var(--text-secondary, #4b5563);
  line-height: 1.6;
}

[data-theme="dark"] .audit-summary {
  color: var(--text-secondary-dark, #9ca3af);
}

.audit-markdown {
  margin-top: 8px;
}

.audit-fallback {
  padding: 12px;
  background: var(--bg-subtle, #f8f9fa);
  border-radius: 6px;
  color: var(--text-secondary, #4b5563);
}

[data-theme="dark"] .audit-fallback {
  background: #111827;
  color: #9ca3af;
}

.audit-file-url {
  font-family: ui-monospace, monospace;
  font-size: 13px;
  word-break: break-all;
  color: #6366f1;
  margin-bottom: 8px;
}

.audit-section {
  margin-bottom: 20px;
}

.audit-section h3 {
  font-size: 14px;
  color: var(--text-secondary, #6b7280);
  margin-bottom: 8px;
}

[data-theme="dark"] .audit-section h3 {
  color: #9ca3af;
}

/* 弹窗 loading mask 深色适配 */
.audit-detail-dialog :deep(.el-loading-mask) {
  background-color: rgba(255, 255, 255, 0.7);
}

[data-theme="dark"] .audit-detail-dialog :deep(.el-loading-mask) {
  background-color: rgba(31, 41, 55, 0.85);
}

.audit-attachment-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px;
  background: var(--bg-subtle, #f8f9fa);
  border-radius: 4px;
  margin-bottom: 6px;
  font-size: 14px;
}

[data-theme="dark"] .audit-attachment-item {
  background: #111827;
}
</style>
