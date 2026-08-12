<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import MarkdownPreview from '@/components/MarkdownPreview.vue'
import { request } from '@/api/client'
import type { ResourceAttachmentItem, UploadedFileItem } from '@/stores/types'
import {
  attachmentPreviewKind,
  inlineResourceFileUrl,
  storedResourceFilePath
} from '@/utils/attachmentPreview'

interface TextPreview {
  content: string
  truncated: boolean
  fileSize: number
}

interface ArchiveEntry {
  name: string
  directory: boolean
  size: number
  compressedSize: number
}

interface ArchivePreview {
  entries: ArchiveEntry[]
  totalEntries: number
  truncated: boolean
}

type PreviewAttachment = ResourceAttachmentItem | UploadedFileItem

const props = defineProps<{
  modelValue: boolean
  attachment: PreviewAttachment | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'download', attachment: PreviewAttachment): void
}>()

const loading = ref(false)
const errorMessage = ref('')
const textPreview = ref<TextPreview | null>(null)
const archivePreview = ref<ArchivePreview | null>(null)

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})
const fileName = computed(() => {
  if (!props.attachment) return ''
  return 'fileName' in props.attachment
    ? props.attachment.fileName
    : props.attachment.originalName
})
const kind = computed(() => attachmentPreviewKind(
  fileName.value,
  props.attachment?.fileUrl || ''
))
const inlineUrl = computed(() => props.attachment?.fileUrl
  ? inlineResourceFileUrl(props.attachment.fileUrl)
  : '')

watch(
  () => [props.modelValue, props.attachment?.fileUrl] as const,
  ([isVisible]) => {
    if (isVisible) void loadPreview()
  }
)

async function loadPreview() {
  textPreview.value = null
  archivePreview.value = null
  errorMessage.value = ''
  if (!props.attachment) return
  if (!['markdown', 'text', 'archive'].includes(kind.value)) return

  const path = storedResourceFilePath(props.attachment.fileUrl)
  if (!path) {
    errorMessage.value = '仅支持预览本站上传的附件'
    return
  }

  loading.value = true
  try {
    if (kind.value === 'archive') {
      archivePreview.value = await request<ArchivePreview>('/api/resource/files/preview/archive', {
        query: { path }
      })
    } else {
      textPreview.value = await request<TextPreview>('/api/resource/files/preview/text', {
        query: { path }
      })
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '附件预览加载失败'
  } finally {
    loading.value = false
  }
}

function formatSize(size: number) {
  if (size < 0) return '未知'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function download() {
  if (!props.attachment) return
  emit('download', props.attachment)
}

function reportFrameError() {
  ElMessage.warning('浏览器无法内嵌显示该文件，请下载后查看')
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="`附件预览 · ${fileName}`"
    width="min(1000px, 94vw)"
    destroy-on-close
    append-to-body
    class="attachment-preview-dialog"
  >
    <div v-loading="loading" class="preview-body">
      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        :closable="false"
        show-icon
      />

      <iframe
        v-else-if="kind === 'pdf'"
        :src="inlineUrl"
        class="pdf-preview"
        title="PDF 预览"
        @error="reportFrameError"
      />

      <img
        v-else-if="kind === 'image'"
        :src="inlineUrl"
        :alt="fileName"
        class="image-preview"
      />

      <template v-else-if="kind === 'markdown' && textPreview">
        <el-alert
          v-if="textPreview.truncated"
          title="文件较大，仅预览前 512KB 内容"
          type="warning"
          :closable="false"
          show-icon
          class="preview-alert"
        />
        <MarkdownPreview
          :model-value="textPreview.content"
          editor-id="attachment-markdown-preview"
        />
      </template>

      <template v-else-if="kind === 'text' && textPreview">
        <el-alert
          v-if="textPreview.truncated"
          title="文件较大，仅预览前 512KB 内容"
          type="warning"
          :closable="false"
          show-icon
          class="preview-alert"
        />
        <pre class="text-preview">{{ textPreview.content }}</pre>
      </template>

      <template v-else-if="kind === 'archive' && archivePreview">
        <div class="archive-summary">
          <span>共 {{ archivePreview.totalEntries }} 项</span>
          <el-tag v-if="archivePreview.truncated" type="warning">仅显示前 500 项</el-tag>
        </div>
        <el-table :data="archivePreview.entries" height="470" size="small">
          <el-table-column label="名称" min-width="440">
            <template #default="{ row }">
              {{ row.directory ? '📁' : '📄' }} {{ row.name }}
            </template>
          </el-table-column>
          <el-table-column label="原始大小" width="120">
            <template #default="{ row }">{{ row.directory ? '—' : formatSize(row.size) }}</template>
          </el-table-column>
          <el-table-column label="压缩后" width="120">
            <template #default="{ row }">{{ row.directory ? '—' : formatSize(row.compressedSize) }}</template>
          </el-table-column>
        </el-table>
      </template>

      <el-result
        v-else-if="kind === 'unsupported'"
        icon="info"
        title="暂不支持在线预览"
        sub-title="RAR、7Z 和 Office 文件请下载后使用本地软件查看；ZIP 可直接查看目录。"
      />
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" @click="download">下载原文件</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.preview-body {
  min-height: 440px;
}

.pdf-preview {
  width: 100%;
  height: 70vh;
  min-height: 520px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}

.image-preview {
  display: block;
  max-width: 100%;
  max-height: 70vh;
  margin: 0 auto;
  object-fit: contain;
}

.text-preview {
  max-height: 65vh;
  margin: 0;
  padding: 18px;
  overflow: auto;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.65;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.preview-alert {
  margin-bottom: 12px;
}

.archive-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

@media (max-width: 720px) {
  .pdf-preview {
    min-height: 420px;
  }
}
</style>
