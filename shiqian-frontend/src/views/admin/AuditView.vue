<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminLayout from '@/components/AdminLayout.vue'
import AttachmentPreviewDialog from '@/components/AttachmentPreviewDialog.vue'
import StatusTag from '@/components/StatusTag.vue'
import { buildApiUrl } from '@/api/client'
import {
  contentSceneLabel,
  useAppStore,
  type ResourceAttachmentItem,
  type ResourceItem,
  type ResourceStatus
} from '@/stores/app'

const MarkdownPreview = defineAsyncComponent(() => import('@/components/MarkdownPreview.vue'))
const store = useAppStore()
const searchText = ref('')
const statusFilter = ref<'全部' | ResourceStatus>('待审核')
const current = ref<ResourceItem | null>(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const decisionLoading = ref(false)
const reviewReason = ref('')
const selectedRows = ref<ResourceItem[]>([])
const previewVisible = ref(false)
const previewAttachment = ref<ResourceAttachmentItem | null>(null)

const auditResources = computed(() => {
  const text = searchText.value.trim().toLowerCase()
  return store.resources
    .filter(item => ['待审核', '待修改', '已拒绝'].includes(item.status))
    .filter(item => statusFilter.value === '全部' || item.status === statusFilter.value)
    .filter(item => !text || `${item.title}${item.author}${item.tags || ''}${item.desc}`.toLowerCase().includes(text))
    .sort((a, b) => b.id - a.id)
})

const pendingCount = computed(() => store.pendingResources.length)

onMounted(() => {
  store.loadHomeData().catch(error => {
    ElMessage.error(error instanceof Error ? error.message : '审核队列加载失败')
  })
})

async function openDetail(row: ResourceItem) {
  current.value = row
  reviewReason.value = row.reviewReason || ''
  detailVisible.value = true
  detailLoading.value = true
  try {
    await store.loadResourceDetail(row.id, { includeFavorite: false })
    current.value = store.getResource(row.id) || row
    reviewReason.value = current.value.reviewReason || ''
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资源详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function openAttachment(url?: string) {
  if (!url) return
  window.open(buildApiUrl(url), '_blank')
}

function previewFile(attachment: ResourceAttachmentItem) {
  previewAttachment.value = attachment
  previewVisible.value = true
}

function downloadPreviewAttachment(attachment: { fileUrl: string }) {
  openAttachment(attachment.fileUrl)
}

function nextPendingId(excludeId: number) {
  return store.pendingResources.find(item => item.id !== excludeId)?.id
}

async function decide(status: 1 | 2 | 3) {
  if (!current.value) return
  const reason = reviewReason.value.trim()
  if ((status === 2 || status === 3) && !reason) {
    ElMessage.warning('退回修改或拒绝时必须填写审核意见')
    return
  }

  const currentId = current.value.id
  const nextId = nextPendingId(currentId)
  decisionLoading.value = true
  try {
    if (status === 1) {
      await store.approveResource(currentId)
      ElMessage.success('审核通过，资源已发布')
    } else if (status === 2) {
      await store.requestResourceChanges(currentId, reason)
      ElMessage.warning('已退回作者修改')
    } else {
      await store.rejectResource(currentId, reason)
      ElMessage.warning('资源已拒绝')
    }

    if (nextId) {
      const next = store.getResource(nextId)
      if (next) await openDetail(next)
    } else {
      detailVisible.value = false
      current.value = null
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审核操作失败')
  } finally {
    decisionLoading.value = false
  }
}

function onSelectionChange(rows: ResourceItem[]) {
  selectedRows.value = rows.filter(item => item.status === '待审核')
}

async function batchApprove() {
  if (!selectedRows.value.length) return
  try {
    await ElMessageBox.confirm(
      `确认批量通过选中的 ${selectedRows.value.length} 条待审核资源？`,
      '批量审核',
      { type: 'warning' }
    )
    const results = await Promise.allSettled(
      selectedRows.value.map(item => store.approveResource(item.id))
    )
    const failed = results.filter(item => item.status === 'rejected').length
    if (failed) {
      ElMessage.warning(`已通过 ${results.length - failed} 条，${failed} 条处理失败`)
    } else {
      ElMessage.success(`已通过 ${results.length} 条资源`)
    }
    selectedRows.value = []
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '批量审核失败')
    }
  }
}
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>审核工作台</h1>
        <p class="sub">集中预览博客、图片和资料，检查正文、图片及附件。</p>
      </div>
      <el-tag type="warning" size="large">待审核 {{ pendingCount }}</el-tag>
    </div>

    <div class="audit-toolbar panel">
      <el-input
        v-model="searchText"
        clearable
        placeholder="搜索标题、作者、标签或摘要"
        style="width: 320px"
      />
      <el-radio-group v-model="statusFilter">
        <el-radio-button label="待审核" value="待审核" />
        <el-radio-button label="待修改" value="待修改" />
        <el-radio-button label="已拒绝" value="已拒绝" />
        <el-radio-button label="全部" value="全部" />
      </el-radio-group>
      <el-button
        type="primary"
        plain
        :disabled="!selectedRows.length"
        @click="batchApprove"
      >
        批量通过（{{ selectedRows.length }}）
      </el-button>
    </div>

    <el-table
      :data="auditResources"
      class="panel audit-table"
      row-key="id"
      @selection-change="onSelectionChange"
      @row-dblclick="openDetail"
    >
      <el-table-column type="selection" width="46" :selectable="(row: ResourceItem) => row.status === '待审核'" />
      <el-table-column label="内容" min-width="320">
        <template #default="{ row }">
          <button class="title-button" @click="openDetail(row)">{{ row.title }}</button>
          <div class="sub one-line">{{ row.desc || '暂无摘要' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="频道" width="100"><template #default="{ row }">{{ contentSceneLabel(row.scene) }}</template></el-table-column>
      <el-table-column prop="author" label="发布者" width="130" />
      <el-table-column label="内容" width="120">
        <template #default="{ row }">
          {{ row.contentMarkdown ? '正文' : '' }}
          {{ row.attachments?.length ? `附件 ${row.attachments.length}` : '' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><StatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="审核意见" min-width="180">
        <template #default="{ row }">
          <span class="sub">{{ row.reviewReason || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="openDetail(row)">审阅</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!auditResources.length" description="当前筛选下没有资源" />

    <el-drawer
      v-model="detailVisible"
      title="资源审阅"
      size="min(780px, 92vw)"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="review-drawer">
        <div class="review-heading">
          <div>
            <h2>{{ current?.title }}</h2>
            <div class="review-meta">
              <span>{{ contentSceneLabel(current?.scene) }}</span>
              <span>{{ current?.author }}</span>
              <StatusTag v-if="current" :status="current.status" />
            </div>
          </div>
        </div>

        <section class="review-section">
          <h3>资源摘要</h3>
          <p>{{ current?.summary || current?.desc || '未填写摘要' }}</p>
        </section>

        <section class="review-section">
          <h3>正文内容</h3>
          <MarkdownPreview
            v-if="current?.contentMarkdown"
            :model-value="current.contentMarkdown"
            class="review-content"
          />
          <el-empty v-else description="该资源以附件为主，没有正文" :image-size="60" />
        </section>

        <section class="review-section">
          <h3>附件（{{ current?.attachments?.length || 0 }}）</h3>
          <div v-if="current?.attachments?.length" class="attachment-list">
            <div
              v-for="attachment in current.attachments"
              :key="attachment.id || attachment.fileUrl"
              class="attachment-row"
              @click="previewFile(attachment)"
            >
              <span>{{ attachment.fileName }}</span>
              <span class="attachment-actions">
                <span class="sub">{{ attachment.fileType || '文件' }}</span>
                <el-button size="small" @click.stop="previewFile(attachment)">预览</el-button>
                <el-button size="small" type="primary" @click.stop="openAttachment(attachment.fileUrl)">下载</el-button>
              </span>
            </div>
          </div>
          <el-empty v-else description="没有附件" :image-size="60" />
        </section>

        <section v-if="current?.reviewTime || current?.reviewReason" class="review-section">
          <h3>最近审核记录</h3>
          <p class="sub">{{ current.reviewTime || '—' }} · {{ current.reviewReason || '无意见' }}</p>
        </section>
      </div>

      <template #footer>
        <div class="decision-panel">
          <el-input
            v-model="reviewReason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="退回修改或拒绝时必须填写具体原因，例如：附件无法打开、内容不完整。"
          />
          <div class="decision-actions">
            <span class="sub">通过后将直接公开；退回修改后作者可以再次提交。</span>
            <div>
              <el-button :loading="decisionLoading" @click="decide(3)">拒绝</el-button>
              <el-button type="warning" plain :loading="decisionLoading" @click="decide(2)">退回修改</el-button>
              <el-button type="success" :loading="decisionLoading" @click="decide(1)">通过并发布</el-button>
            </div>
          </div>
        </div>
      </template>
    </el-drawer>
    <AttachmentPreviewDialog
      v-model="previewVisible"
      :attachment="previewAttachment"
      @download="downloadPreviewAttachment"
    />
  </AdminLayout>
</template>

<style scoped>
.audit-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  padding: 14px;
  margin-bottom: 14px;
}

.title-button,
.attachment-row {
  border: 0;
  background: none;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.title-button {
  padding: 0;
  font: inherit;
  font-weight: 700;
}

.title-button:hover {
  color: var(--el-color-primary);
}

.one-line {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-drawer {
  padding: 0 4px 120px;
}

.review-heading h2 {
  margin: 0 0 10px;
}

.review-meta {
  display: flex;
  gap: 12px;
  align-items: center;
  color: var(--el-text-color-secondary);
}

.review-section {
  margin-top: 24px;
}

.review-section h3 {
  margin: 0 0 10px;
  font-size: 15px;
}

.review-content {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 14px;
}

.attachment-list {
  display: grid;
  gap: 8px;
}

.attachment-row {
  display: flex;
  justify-content: space-between;
  width: 100%;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.attachment-row:hover {
  border-color: var(--el-color-primary);
}

.attachment-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.decision-panel {
  display: grid;
  gap: 12px;
}

.decision-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
</style>
