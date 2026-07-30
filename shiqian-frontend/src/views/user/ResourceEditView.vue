<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadRawFile, UploadUserFile } from 'element-plus'
import { Close, UploadFilled } from '@element-plus/icons-vue'
import {
  CONTENT_SCENES,
  useAppStore,
  type ContentScene,
  type UploadedFileItem,
  type ResourceAttachmentItem
} from '@/stores/app'
import MarkdownLiveEditor from '@/components/MarkdownLiveEditor.vue'
import {
  MAX_RESOURCE_FILE_COUNT,
  RESOURCE_FILE_ACCEPT,
  formatFileSize,
  uploadFilesByTier,
  validateResourceFile,
  type UploadTierName
} from '@/utils/resourceUpload'

const route = useRoute()
const router = useRouter()
const store = useAppStore()

const resourceId = computed(() => Number(route.params.id))
const loading = ref(false)
const submitting = ref(false)
const uploading = ref(false)
const selectedFiles = ref<UploadUserFile[]>([])
const existingAttachments = ref<ResourceAttachmentItem[]>([])
const uploadedFiles = ref<UploadedFileItem[]>([])  // 新增/替换的附件（编辑时支持多）
const uploadProgress = ref(0)
const uploadErrors = ref<string[]>([])
const uploadStage = ref('')
let uploadController: AbortController | null = null
let currentUploadPromise: Promise<void> | null = null
let autoUploadTimer: number | null = null

const form = reactive({
  title: '',
  cat: '',
  categories: [] as string[],
  scene: 'SHARE' as ContentScene,
  tags: '',
  tagNames: [] as string[],
  summary: '',
  contentMarkdown: '',
  changeDescription: ''
})

const canSubmit = computed(() => {
  const hasFiles = selectedFiles.value.length > 0
    || existingAttachments.value.length > 0
    || uploadedFiles.value.length > 0
  return Boolean(resourceId.value && form.title.trim() && (form.contentMarkdown.trim() || hasFiles))
})
const attachmentCount = computed(() => existingAttachments.value.length + uploadedFiles.value.length)

watch(
  () => selectedFiles.value.map(item => item.uid).join(','),
  () => {
    if (selectedFiles.value.length && !uploading.value) scheduleImmediateUpload()
  }
)

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

function fillForm() {
  const resource = store.getResource(resourceId.value)
  if (!resource) return false

  form.title = resource.title
  form.cat = resource.categoryId ? resource.cat : ''
  form.categories = resource.categoryNames?.length
    ? [...resource.categoryNames]
    : resource.categoryId ? [resource.cat] : []
  form.scene = resource.scene
  form.tags = resource.tags || ''
  form.tagNames = resource.tagNames?.length
    ? [...resource.tagNames]
    : (resource.tags || '').split(/[,，]/).map(tag => tag.trim()).filter(Boolean)
  form.summary = resource.summary || resource.desc || ''
  form.contentMarkdown = resource.contentMarkdown || ''

  // 加载现有附件（支持多附件编辑）
  if (resource.attachments && resource.attachments.length > 0) {
    existingAttachments.value = [...resource.attachments]
  } else if (resource.fileUrl) {
    // legacy 单个文件兼容
    existingAttachments.value = [{
      fileName: resource.title || 'legacy-file',
      fileUrl: resource.fileUrl,
      fileSize: resource.fileSize || 0,
      fileType: resource.type
    } as ResourceAttachmentItem]
  } else {
    existingAttachments.value = []
  }
  return true
}

onMounted(async () => {
  if (!resourceId.value) {
    ElMessage.error('资源 ID 无效')
    router.push('/mine')
    return
  }

  loading.value = true
  try {
    await store.loadCategories()
    await store.loadResourceDetail(resourceId.value)
    if (!fillForm()) {
      ElMessage.error('资源不存在或无权访问')
      router.push('/mine')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资源加载失败')
    router.push('/mine')
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
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
  const remainingCount = Math.max(0, MAX_RESOURCE_FILE_COUNT - attachmentCount.value)
  const acceptedFiles = files.slice(0, remainingCount)
  if (files.length > remainingCount) {
    uploadErrors.value.push(`每个资源最多保留 ${MAX_RESOURCE_FILE_COUNT} 个附件`)
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
    ElMessage.success(`已上传 ${result.results.length} 个新附件`)
  } finally {
    uploading.value = false
    uploadController = null
    uploadStage.value = ''
  }
}

function removeSelectedFile(index: number) {
  selectedFiles.value.splice(index, 1)
  uploadErrors.value = []
  uploadProgress.value = 0
}

function removeExistingAttachment(index: number) {
  existingAttachments.value.splice(index, 1)
}

function removeUploadedFile(index: number) {
  uploadedFiles.value.splice(index, 1)
}

function cancelUpload() {
  uploadController?.abort()
  uploadStage.value = '正在取消上传'
}

function handleExceed() {
  ElMessage.warning(`每个资源最多保留 ${MAX_RESOURCE_FILE_COUNT} 个附件`)
}

async function submit() {
  if (!canSubmit.value) {
    ElMessage.warning('请填写标题，并至少保留正文、图片或一个附件')
    return
  }

  submitting.value = true
  try {
    if (currentUploadPromise) await currentUploadPromise
    if (selectedFiles.value.length) {
      await uploadSelectedFiles()
    }

    // 提交完整附件列表：保留的现有 + 新上传的（后端将全量替换）
    const finalAttachments = [
      ...existingAttachments.value,
      ...uploadedFiles.value
    ]

    await store.updateResource(resourceId.value, {
      title: form.title,
      cat: form.cat || undefined,
      categories: form.categories,
      contentScene: form.scene,
      tags: form.tags,
      tagNames: form.tagNames,
      summary: form.summary,
      contentMarkdown: form.contentMarkdown,
      changeDescription: form.changeDescription,
      // 始终传递 attachments（即使空数组也表示清空所有），以触发多附件编辑逻辑
      attachments: finalAttachments.length ? finalAttachments : []
    })

    ElMessage.success('资源已更新')
    router.push(`/detail/${resourceId.value}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section>
    <div class="page-title">
      <div>
        <h1>编辑内容</h1>
        <p class="sub">频道只影响展示；正文、图片和附件满足其一即可。</p>
      </div>
    </div>

    <el-card v-loading="loading" class="form-card" shadow="never">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item label="标题">
              <el-input v-model="form.title" placeholder="例如：Java 课程设计项目模板" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="内容频道">
              <el-select v-model="form.scene" class="full">
                <el-option v-for="scene in CONTENT_SCENES" :key="scene.value" :label="scene.label" :value="scene.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="分类（选填，可多选）">
              <el-select
                v-model="form.categories"
                class="full"
                multiple
                clearable
                collapse-tags
                collapse-tags-tooltip
                placeholder="最多选择 10 个分类"
              >
                <el-option v-for="category in store.categories" :key="category" :label="category" :value="category" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="自由标签（选填，可直接输入后回车）">
              <el-select
                v-model="form.tagNames"
                class="full"
                multiple
                filterable
                allow-create
                default-first-option
                :multiple-limit="20"
                placeholder="最多 20 个标签"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="附件（多附件支持：可移除现有，添加新上传；保存时全量替换）">
              <el-alert
                title="支持 PDF、Office、TXT/Markdown、图片、压缩包及常见源码；选中后立即上传。"
                type="info"
                :closable="false"
                show-icon
                style="margin-bottom: 12px"
              />
              <el-upload
                v-model:file-list="selectedFiles"
                drag
                multiple
                action="#"
                :accept="RESOURCE_FILE_ACCEPT"
                :limit="MAX_RESOURCE_FILE_COUNT"
                :disabled="uploading || attachmentCount >= MAX_RESOURCE_FILE_COUNT"
                :on-exceed="handleExceed"
                :auto-upload="false"
                :show-file-list="false"
                class="upload-panel"
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">点击或拖拽文件到此处，可多选添加更多附件</div>
              </el-upload>
            </el-form-item>

            <!-- 待上传的选中文件（支持移除） -->
            <div v-if="selectedFiles.length" class="uploaded-list">
              <div
                v-for="(file, index) in selectedFiles"
                :key="file.uid ?? index"
                class="uploaded-row"
                style="display:flex; align-items:center; justify-content:space-between; gap:12px;"
              >
                <div style="flex:1; min-width:0; overflow:hidden;">
                  <b style="word-break:break-all;">{{ file.name }}</b>
                  <span v-if="file.size != null" class="sub">{{ formatFileSize(file.size) }}</span>
                </div>
                <el-button size="small" type="danger" text :icon="Close" title="移除此文件" @click="removeSelectedFile(index)" />
              </div>
            </div>

            <div v-if="uploading" class="edit-upload-progress">
              <div><span>{{ uploadStage }}</span><el-button text type="danger" @click="cancelUpload">取消上传</el-button></div>
              <el-progress :percentage="uploadProgress" :stroke-width="18" :text-inside="true" />
            </div>
            <div v-if="uploadErrors.length" style="margin: 4px 0; padding: 6px 8px; background: #fef0f0; color: #f56c6c; font-size: 12px; border-radius: 4px; line-height: 1.4;">
              验证/上传问题：{{ uploadErrors.join('； ') }}
            </div>

            <!-- 现有附件列表（可逐个移除，编辑多附件核心） -->
            <div v-if="existingAttachments.length" class="uploaded-list">
              <div
                v-for="(file, index) in existingAttachments"
                :key="'exist-' + (file.fileUrl || index)"
                class="uploaded-row"
                style="display:flex; align-items:center; justify-content:space-between; gap:12px;"
              >
                <div style="flex:1; min-width:0; overflow:hidden;">
                  <b style="word-break:break-all;">现有：{{ file.fileName }}</b>
                  <span class="sub">{{ file.fileType || '文件' }} · {{ file.fileSize }} 字节</span>
                </div>
                <el-button size="small" type="danger" text :icon="Close" title="移除此现有附件" @click="removeExistingAttachment(index)" />
              </div>
            </div>

            <!-- 新上传的附件（可移除） -->
            <div v-if="uploadedFiles.length" class="uploaded-list">
              <div
                v-for="(file, index) in uploadedFiles"
                :key="'new-' + (file.fileUrl || index)"
                class="uploaded-row"
                style="display:flex; align-items:center; justify-content:space-between; gap:12px;"
              >
                <div style="flex:1; min-width:0; overflow:hidden;">
                  <b style="word-break:break-all;">新上传：{{ file.originalName }}</b>
                  <span class="sub">{{ file.fileType || '文件' }} · {{ file.fileSize }} 字节</span>
                </div>
                <el-button size="small" type="danger" text :icon="Close" title="移除此新附件" @click="removeUploadedFile(index)" />
              </div>
            </div>
          </el-col>
          <el-col :span="24">
            <el-form-item label="简短说明（选填）">
              <el-input
                v-model="form.summary"
                type="textarea"
                :rows="2"
                placeholder="一句话总结这个资源（用于卡片展示和搜索结果）"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="本次修改说明（选填）">
              <el-input
                v-model="form.changeDescription"
                maxlength="500"
                show-word-limit
                placeholder="例如：补充附件并修正文中示例"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="正文内容（选填，实时预览）">
              <MarkdownLiveEditor
                v-model="form.contentMarkdown"
                placeholder="可以只写正文，也可以只保留图片或附件。"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-button type="primary" :loading="submitting" :disabled="!canSubmit || uploading" @click="submit">保存修改</el-button>
        <el-button @click="router.back()">取消</el-button>
      </el-form>
    </el-card>
  </section>
</template>

<style scoped>
.simple-preview {
  width: 100%;
  min-height: 220px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}

.edit-upload-progress {
  margin: 8px 0;
}

.edit-upload-progress > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
