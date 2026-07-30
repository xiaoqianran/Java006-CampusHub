<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRawFile, UploadUserFile } from 'element-plus'
import { Close, Document, EditPen, Picture, UploadFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import AttachmentPreviewDialog from '@/components/AttachmentPreviewDialog.vue'
import MarkdownLiveEditor from '@/components/MarkdownLiveEditor.vue'
import {
  useAppStore,
  type ContentScene,
  type UploadedFileItem
} from '@/stores/app'
import { buildApiUrl } from '@/api/client'
import {
  MAX_RESOURCE_FILE_COUNT,
  RESOURCE_FILE_ACCEPT,
  formatFileSize,
  uploadFilesByTier,
  validateResourceFile,
  type UploadTierName
} from '@/utils/resourceUpload'

const router = useRouter()
const store = useAppStore()
const submitting = ref(false)
const uploading = ref(false)
const selectedFiles = ref<UploadUserFile[]>([])
const uploadedFiles = ref<UploadedFileItem[]>([])
const uploadProgress = ref(0)
const uploadErrors = ref<string[]>([])
const uploadStage = ref('')
const previewVisible = ref(false)
const previewAttachment = ref<UploadedFileItem | null>(null)
let uploadController: AbortController | null = null
let currentUploadPromise: Promise<void> | null = null
let autoUploadTimer: number | null = null

const form = reactive({
  scene: 'BLOG' as ContentScene,
  title: '',
  cat: '',
  categories: [] as string[],
  tags: '',
  tagNames: [] as string[],
  summary: '',
  contentMarkdown: ''
})

const hasFiles = computed(() => selectedFiles.value.length > 0 || uploadedFiles.value.length > 0)
const hasText = computed(() => Boolean(form.contentMarkdown.trim()))
const canSubmit = computed(() => {
  return Boolean(form.title.trim() && (hasText.value || hasFiles.value))
})

const DRAFT_KEY = 'shiqian_publish_draft'
let saveTimer: number | null = null

function draftPayload() {
  return {
    scene: form.scene,
    title: form.title,
    cat: form.cat,
    categories: form.categories,
    tags: form.tags,
    tagNames: form.tagNames,
    summary: form.summary,
    contentMarkdown: form.contentMarkdown,
    attachments: uploadedFiles.value
  }
}

function applyDraft(data: any) {
  Object.assign(form, {
    // 旧草稿兼容：文章归入博客，文件/图文归入资料频道。
    scene: ['BLOG', 'GALLERY', 'SHARE'].includes(data.scene)
      ? data.scene
      : data.mode === 'ARTICLE' ? 'BLOG' : 'SHARE',
    title: data.title || '',
    cat: data.cat || '',
    categories: Array.isArray(data.categories)
      ? data.categories
      : data.cat ? [data.cat] : [],
    tags: data.tags || '',
    tagNames: Array.isArray(data.tagNames)
      ? data.tagNames
      : (data.tags || '').split(/[,，]/).map((tag: string) => tag.trim()).filter(Boolean),
    summary: data.summary || '',
    contentMarkdown: data.contentMarkdown || ''
  })
  uploadedFiles.value = Array.isArray(data.attachments) ? data.attachments : []
}

function scheduleAutoSave() {
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = window.setTimeout(() => {
    const payload = draftPayload()
    if (payload.title || payload.summary || payload.contentMarkdown || payload.attachments.length) {
      localStorage.setItem(DRAFT_KEY, JSON.stringify(payload))
    }
  }, 1000)
}

function saveDraft(showToast = true) {
  localStorage.setItem(DRAFT_KEY, JSON.stringify(draftPayload()))
  if (showToast) ElMessage.success('草稿已保存')
}

function loadDraft() {
  const raw = localStorage.getItem(DRAFT_KEY)
  if (!raw) {
    ElMessage.info('暂无草稿')
    return
  }
  try {
    applyDraft(JSON.parse(raw))
    ElMessage.success('已加载草稿')
  } catch {
    ElMessage.error('草稿数据异常')
  }
}

function clearDraft() {
  localStorage.removeItem(DRAFT_KEY)
  ElMessage.success('草稿已清除')
}

watch(form, scheduleAutoSave, { deep: true })
watch(uploadedFiles, scheduleAutoSave, { deep: true })
watch(
  () => form.categories,
  categories => {
    form.cat = categories[0] || ''
  },
  { deep: true }
)
watch(
  () => form.tagNames,
  tags => {
    form.tags = tags.join(',')
  },
  { deep: true }
)
watch(
  () => selectedFiles.value.map(item => item.uid).join(','),
  () => {
    if (selectedFiles.value.length && !uploading.value) scheduleImmediateUpload()
  }
)

onMounted(() => {
  store.loadCategories().catch(() => undefined)
  const raw = localStorage.getItem(DRAFT_KEY)
  if (!raw) return
  ElMessageBox.confirm('检测到未提交的草稿，是否恢复？', '恢复草稿', {
    confirmButtonText: '恢复',
    cancelButtonText: '稍后',
    type: 'info'
  }).then(() => {
    try {
      applyDraft(JSON.parse(raw))
    } catch {
      localStorage.removeItem(DRAFT_KEY)
    }
  }).catch(() => undefined)
})

onUnmounted(() => {
  if (saveTimer) clearTimeout(saveTimer)
  if (autoUploadTimer) clearTimeout(autoUploadTimer)
  uploadController?.abort()
})

function scheduleImmediateUpload() {
  if (autoUploadTimer) clearTimeout(autoUploadTimer)
  autoUploadTimer = window.setTimeout(() => {
    autoUploadTimer = null
    void uploadSelectedFiles().catch(error => {
      if (!(error instanceof DOMException && error.name === 'AbortError')) {
        ElMessage.error(error instanceof Error ? error.message : '附件上传失败')
      }
    })
  }, 120)
}

async function uploadSelectedFiles() {
  if (currentUploadPromise) return currentUploadPromise

  const task = performSelectedUpload()
  currentUploadPromise = task
  try {
    await task
  } finally {
    if (currentUploadPromise === task) currentUploadPromise = null
  }
}

async function performSelectedUpload() {
  const pendingItems = [...selectedFiles.value]
  selectedFiles.value = []
  const files = pendingItems
    .map(item => item.raw)
    .filter((item): item is UploadRawFile => Boolean(item))
  if (!files.length) return

  uploadErrors.value = []
  const remainingCount = Math.max(0, MAX_RESOURCE_FILE_COUNT - uploadedFiles.value.length)
  const acceptedFiles = files.slice(0, remainingCount)
  if (files.length > remainingCount) {
    uploadErrors.value.push(`每个资源最多上传 ${MAX_RESOURCE_FILE_COUNT} 个附件`)
  }
  const validFiles = acceptedFiles.filter(file => {
    const message = validateResourceFile(file)
    if (message) uploadErrors.value.push(message)
    return !message
  })
  if (!validFiles.length) throw new Error('没有可以上传的文件')

  uploading.value = true
  uploadProgress.value = 0
  uploadController = new AbortController()
  try {
    const result = await uploadFilesByTier<UploadedFileItem>(validFiles, {
      signal: uploadController.signal,
      retries: 1,
      onProgress: percentage => {
        uploadProgress.value = percentage
      },
      onTierChange: (tier: UploadTierName, concurrency: number) => {
        uploadStage.value = `${tier}上传中（并发 ${concurrency}）`
      },
      onRetry: file => {
        uploadStage.value = `${file.name} 上传失败，正在自动重试`
      },
      onFileUploaded: (_file, uploadedFile) => {
        uploadedFiles.value = [...uploadedFiles.value, uploadedFile]
      },
      worker: async (file, onProgress) => {
        const response = await store.uploadFiles([file], {
          signal: uploadController?.signal,
          onProgress
        })
        if (!response[0]) throw new Error('上传接口未返回文件信息')
        return response[0]
      }
    })

    result.failures.forEach(({ file, error }) => {
      uploadErrors.value.push(`${file.name}：${error instanceof Error ? error.message : '上传失败'}`)
    })
    if (!result.results.length) throw new Error('附件上传失败')
    ElMessage.success(`已上传 ${result.results.length} 个附件`)
  } finally {
    uploading.value = false
    uploadController = null
    uploadStage.value = ''
  }
}

function removeSelectedFile(index: number) {
  selectedFiles.value.splice(index, 1)
}

function removeUploadedFile(index: number) {
  uploadedFiles.value.splice(index, 1)
}

function previewFile(file: UploadedFileItem) {
  previewAttachment.value = file
  previewVisible.value = true
}

function downloadAttachment(file: { fileUrl: string }) {
  window.open(buildApiUrl(file.fileUrl), '_blank')
}

function cancelUpload() {
  uploadController?.abort()
  uploadStage.value = '正在取消上传'
}

function handleExceed() {
  ElMessage.warning(`每个资源最多上传 ${MAX_RESOURCE_FILE_COUNT} 个附件`)
}

async function submit() {
  if (!store.logged) {
    ElMessage.warning('请先登录后发布内容')
    router.push('/login')
    return
  }
  if (!canSubmit.value) {
    ElMessage.warning('请填写标题，并至少添加正文、图片或一个附件')
    return
  }

  submitting.value = true
  try {
    if (currentUploadPromise) await currentUploadPromise
    if (selectedFiles.value.length) await uploadSelectedFiles()
    await store.submitResource({
      title: form.title.trim(),
      cat: form.cat || undefined,
      categories: form.categories,
      tags: form.tags.trim(),
      tagNames: form.tagNames,
      summary: form.summary.trim(),
      contentMarkdown: form.contentMarkdown.trim(),
      contentScene: form.scene,
      attachments: uploadedFiles.value
    })
    localStorage.removeItem(DRAFT_KEY)
    ElMessage.success('资源已提交审核')
    router.push('/mine')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="publish-page">
    <div class="page-title">
      <div>
        <h1>发布内容</h1>
        <p class="sub">选择展示频道；正文、图片和附件任意一种非空即可发布。</p>
      </div>
      <span class="draft-hint">内容会自动保存为本地草稿</span>
    </div>

    <el-alert
      v-if="!store.logged"
      title="请先登录后发布内容"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <el-card shadow="never" class="publish-card">
      <section class="form-section">
        <div class="section-heading">
          <span class="step">1</span>
          <div><h2>选择内容频道</h2><p>频道只决定展示方式，不限制你上传的内容。</p></div>
        </div>
        <el-radio-group v-model="form.scene" class="mode-grid">
          <el-radio-button value="BLOG">
            <el-icon><EditPen /></el-icon>
            <b>博客帖</b>
            <span>观点、教程、经验和长文</span>
          </el-radio-button>
          <el-radio-button value="GALLERY">
            <el-icon><Picture /></el-icon>
            <b>图片帖</b>
            <span>作品、相册和视觉内容</span>
          </el-radio-button>
          <el-radio-button value="SHARE">
            <el-icon><Document /></el-icon>
            <b>资料分享帖</b>
            <span>文件、源码、课件和讨论</span>
          </el-radio-button>
        </el-radio-group>
      </section>

      <section class="form-section">
        <div class="section-heading">
          <span class="step">2</span>
          <div><h2>基本信息</h2><p>只要求标题，分类和标签都可以留空。</p></div>
        </div>
        <el-form label-position="top">
          <el-row :gutter="16">
            <el-col :xs="24" :md="16">
              <el-form-item label="标题">
                <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="一句话说明你要分享什么" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="分类（选填，可多选）">
                <el-select
                  v-model="form.categories"
                  class="full"
                  multiple
                  clearable
                  collapse-tags
                  collapse-tags-tooltip
                  :max-collapse-tags="2"
                  placeholder="最多选择 10 个分类"
                >
                  <el-option v-for="category in store.categories" :key="category" :label="category" :value="category" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="自由标签（选填，可直接输入后回车）">
                <el-select
                  v-model="form.tagNames"
                  class="full"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  :multiple-limit="20"
                  placeholder="例如：Java、校园生活、摄影"
                />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="简短说明（选填）">
                <el-input
                  v-model="form.summary"
                  type="textarea"
                  :rows="2"
                  maxlength="500"
                  show-word-limit
                  placeholder="补充适用课程、内容范围或使用方式"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </section>

      <section class="form-section">
        <div class="section-heading">
          <span class="step">3</span>
          <div><h2>图片与附件（选填）</h2><p>所有频道都能上传图片和各种文件，选中后立即上传。</p></div>
        </div>
        <el-alert
          title="支持 PDF、Office、TXT/Markdown、图片、ZIP/RAR/7Z，以及 Java、Python、JavaScript、C/C++、Go、SQL 等常见源码文件。"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 14px"
        />
        <div class="concurrency-hint">
          <span>≤ 2MB：4 个并发</span>
          <span>2–10MB：2 个并发</span>
          <span>＞10MB：逐个上传</span>
        </div>
        <el-upload
          v-model:file-list="selectedFiles"
          drag
          multiple
          action="#"
          :accept="RESOURCE_FILE_ACCEPT"
          :limit="Math.max(1, MAX_RESOURCE_FILE_COUNT - uploadedFiles.length)"
          :disabled="!store.logged || uploading || uploadedFiles.length >= MAX_RESOURCE_FILE_COUNT"
          :on-exceed="handleExceed"
          :auto-upload="false"
          :show-file-list="false"
          class="upload-panel"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">点击或拖拽文件到此处</div>
        </el-upload>

        <div v-if="selectedFiles.length || uploadedFiles.length" class="file-list">
          <div v-for="(file, index) in selectedFiles" :key="file.uid" class="file-row">
            <span>{{ file.name }} <small v-if="file.size">{{ formatFileSize(file.size) }}</small></span>
            <el-button text type="danger" :icon="Close" @click="removeSelectedFile(index)" />
          </div>
          <div v-for="(file, index) in uploadedFiles" :key="file.fileUrl" class="file-row success">
            <span>{{ file.originalName }}</span>
            <span class="sub">已上传</span>
            <el-button text type="primary" @click="previewFile(file)">预览</el-button>
            <el-button text type="danger" :icon="Close" @click="removeUploadedFile(index)" />
          </div>
        </div>
        <div v-if="uploading" class="upload-progress">
          <div><span>{{ uploadStage }}</span><el-button text type="danger" @click="cancelUpload">取消上传</el-button></div>
          <el-progress :percentage="uploadProgress" />
        </div>
        <el-alert v-if="uploadErrors.length" :title="uploadErrors.join('；')" type="error" :closable="false" style="margin-top: 12px" />
      </section>

      <section class="form-section">
        <div class="section-heading">
          <span class="step">4</span>
          <div><h2>正文内容（选填）</h2><p>可以只发正文，也可以只发图片或附件。</p></div>
        </div>
        <MarkdownLiveEditor
          v-model="form.contentMarkdown"
          placeholder="写下正文。普通文字、列表和段落都可以，不需要学习复杂格式。"
        />
      </section>

      <div class="submit-bar">
        <div>
          <b>{{ canSubmit ? '可以提交审核' : '请填写标题并添加至少一种内容' }}</b>
          <p class="sub">提交后可在“我的发布”查看审核进度和反馈。</p>
        </div>
        <div>
          <el-button @click="saveDraft(true)">保存草稿</el-button>
          <el-button @click="loadDraft">恢复草稿</el-button>
          <el-button type="primary" :loading="submitting" :disabled="!canSubmit || uploading" @click="submit">
            提交审核
          </el-button>
        </div>
      </div>
    </el-card>
    <AttachmentPreviewDialog
      v-model="previewVisible"
      :attachment="previewAttachment"
      @download="downloadAttachment"
    />
  </section>
</template>

<style scoped>
.publish-page {
  max-width: 980px;
  margin: 0 auto;
}

.draft-hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.publish-card :deep(.el-card__body) {
  padding: 0;
}

.form-section {
  padding: 26px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.concurrency-hint {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.concurrency-hint span {
  padding: 5px 9px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 999px;
  font-size: 12px;
}

.upload-progress {
  margin-top: 12px;
}

.upload-progress > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.file-row small {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
}

.section-heading {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 18px;
}

.section-heading h2,
.section-heading p {
  margin: 0;
}

.section-heading h2 {
  font-size: 17px;
}

.section-heading p {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.section-heading > .el-button {
  margin-left: auto;
}

.step {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 700;
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  width: 100%;
}

.mode-grid :deep(.el-radio-button__inner) {
  display: grid;
  justify-items: start;
  gap: 6px;
  width: 100%;
  min-height: 112px;
  padding: 18px;
  border: 1px solid var(--el-border-color) !important;
  border-radius: 10px !important;
  box-shadow: none !important;
  white-space: normal;
}

.mode-grid :deep(.el-radio-button__inner span) {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.file-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.file-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.file-row > :first-child {
  flex: 1;
  word-break: break-all;
}

.file-row.success {
  border-color: var(--el-color-success-light-5);
}

.submit-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 26px;
}

.submit-bar p {
  margin: 4px 0 0;
}

@media (max-width: 720px) {
  .mode-grid {
    grid-template-columns: 1fr;
  }

  .submit-bar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
