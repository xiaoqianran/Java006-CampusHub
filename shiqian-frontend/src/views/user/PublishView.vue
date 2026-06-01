<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRawFile, UploadUserFile } from 'element-plus'
import { useRouter } from 'vue-router'
import { Close, UploadFilled } from '@element-plus/icons-vue'
import { useAppStore, type UploadedFileItem } from '@/stores/app'
import MarkdownEditor from '@/components/MarkdownEditor.vue'

const router = useRouter()
const store = useAppStore()
const submitting = ref(false)
const uploading = ref(false)
const selectedFiles = ref<UploadUserFile[]>([])
const uploadedFiles = ref<UploadedFileItem[]>([])
const uploadProgress = ref(0)
const uploadErrors = ref<string[]>([])

const form = reactive({
  title: '',
  cat: '计算机科学',
  summary: '',           // 资源摘要（用于卡片和搜索）
  contentMarkdown: ''    // Markdown 正文
})

const canSubmit = computed(() => {
  return Boolean(form.title && form.cat && form.summary && form.contentMarkdown)
})

watch(selectedFiles, () => {
  uploadedFiles.value = []
  uploadErrors.value = []
  uploadProgress.value = 0
})

// ===== 草稿自动保存（localStorage，简单可靠） =====
const DRAFT_KEY = 'shiqian_publish_draft'
let saveTimer: number | null = null

function scheduleAutoSave() {
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = window.setTimeout(() => {
    const payload = {
      title: form.title,
      cat: form.cat,
      summary: form.summary,
      contentMarkdown: form.contentMarkdown,
      attachments: uploadedFiles.value
    }
    // 仅当有实质内容时保存，避免空草稿
    if (payload.title || payload.summary || payload.contentMarkdown) {
      localStorage.setItem(DRAFT_KEY, JSON.stringify(payload))
    }
  }, 1200) // 防抖 1.2s
}

function saveDraft(showToast = true) {
  const payload = {
    title: form.title,
    cat: form.cat,
    summary: form.summary,
    contentMarkdown: form.contentMarkdown,
    attachments: uploadedFiles.value
  }
  localStorage.setItem(DRAFT_KEY, JSON.stringify(payload))
  if (showToast) {
    ElMessage.success('草稿已保存')
  }
}

function loadDraft() {
  const draftStr = localStorage.getItem(DRAFT_KEY)
  if (!draftStr) {
    ElMessage.info('暂无草稿')
    return
  }
  try {
    const data = JSON.parse(draftStr)
    Object.assign(form, {
      title: data.title || '',
      cat: data.cat || '计算机科学',
      summary: data.summary || '',
      contentMarkdown: data.contentMarkdown || ''
    })
    if (Array.isArray(data.attachments)) {
      uploadedFiles.value = data.attachments
    }
    ElMessage.success('已加载草稿')
  } catch {
    ElMessage.error('草稿数据异常')
  }
}

function clearDraft() {
  localStorage.removeItem(DRAFT_KEY)
  ElMessage.success('草稿已清除')
}

watch(form, () => scheduleAutoSave(), { deep: true })
watch(uploadedFiles, () => scheduleAutoSave(), { deep: true })

onUnmounted(() => {
  if (saveTimer) clearTimeout(saveTimer)
})

onMounted(() => {
  store.loadCategories().catch(() => undefined)
  const draftStr = localStorage.getItem(DRAFT_KEY)
  if (draftStr) {
    ElMessageBox.confirm('检测到未提交的草稿，是否恢复？', '恢复草稿', {
      confirmButtonText: '恢复',
      cancelButtonText: '忽略',
      type: 'info',
      distinguishCancelAndClose: true
    }).then(() => {
      try {
        const data = JSON.parse(draftStr)
        Object.assign(form, {
          title: data.title || '',
          cat: data.cat || '计算机科学',
          summary: data.summary || '',
          contentMarkdown: data.contentMarkdown || ''
        })
        if (Array.isArray(data.attachments)) {
          uploadedFiles.value = data.attachments
        }
        ElMessage.success('草稿已恢复')
      } catch {
        // ignore malformed draft
      }
    }).catch(() => {
      // user chose to ignore; draft remains available via Load Draft button
    })
  }
})

// ===== upload UX helpers (validation + simple per-file progress) =====
function getFileExt(file: { name?: string }): string {
  const n = file?.name || ''
  const i = n.lastIndexOf('.')
  return i > -1 ? n.slice(i + 1).toLowerCase() : ''
}

function isAllowedFile(file: File): boolean {
  const ext = getFileExt(file)
  const allowedExts = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'md', 'jpg', 'jpeg', 'png', 'gif', 'zip', 'rar', '7z']
  if (allowedExts.includes(ext)) return true
  const type = (file as any).type || ''
  return type.startsWith('image/') || type === 'application/pdf' || /word|excel|powerpoint|text|zip|rar/.test(type)
}

const MAX_FILE_SIZE = 50 * 1024 * 1024 // 50MB client-side limit

async function uploadSelectedFiles() {
  let files = selectedFiles.value
    .map(item => item.raw)
    .filter((item): item is UploadRawFile => Boolean(item))
  if (!files.length) {
    throw new Error('请先选择附件')
  }

  // client-side validation: size + type whitelist (pdf/doc/image etc)
  const validFiles: File[] = []
  const errors: string[] = []
  for (const f of files) {
    if (f.size > MAX_FILE_SIZE) {
      errors.push(`${f.name}: 超过50MB限制`)
    } else if (!isAllowedFile(f)) {
      errors.push(`${f.name}: 不支持的文件类型（仅限文档/图片/压缩包）`)
    } else {
      validFiles.push(f)
    }
  }
  if (errors.length) {
    uploadErrors.value = errors
  }
  files = validFiles
  if (!files.length) {
    throw new Error('没有符合要求的文件')
  }

  uploading.value = true
  uploadProgress.value = 0
  uploadErrors.value = errors // keep pre-validation errors
  const uploaded: UploadedFileItem[] = []
  try {
    // sequential upload for per-file progress & granular error feedback (reuse store FormData per call)
    for (let i = 0; i < files.length; i++) {
      const f = files[i]
      try {
        const res = await store.uploadFiles([f])
        if (res && res[0]) uploaded.push(res[0])
      } catch (e: any) {
        errors.push(`${f.name}: ${e?.message || '上传失败'}`)
        uploadErrors.value = [...errors]
      }
      uploadProgress.value = Math.round(((i + 1) / files.length) * 100)
    }
    uploadedFiles.value = uploaded
    const successCount = uploaded.length
    const errorCount = errors.length
    if (successCount) {
      ElMessage.success(`已上传 ${successCount} 个附件${errorCount ? `（${errorCount} 个失败）` : ''}`)
    } else if (errorCount) {
      ElMessage.error('全部文件上传失败')
    }
  } finally {
    uploading.value = false
    // keep final progress briefly, reset on next select via watch
    setTimeout(() => {
      if (!uploading.value) uploadProgress.value = 0
    }, 800)
  }
}

// 单个移除：支持在提交前逐个删除选中的待上传文件或已上传的附件元数据
function removeSelectedFile(index: number) {
  selectedFiles.value.splice(index, 1)
  uploadErrors.value = []
  uploadProgress.value = 0
}

function removeUploadedFile(index: number) {
  uploadedFiles.value.splice(index, 1)
}

async function submit() {
  if (!store.logged) {
    ElMessage.warning('请先登录后发布资源')
    router.push('/login')
    return
  }
  if (!canSubmit.value) {
    ElMessage.warning('请补充标题、分类、摘要和 Markdown 正文')
    return
  }

  submitting.value = true
  try {
    if (selectedFiles.value.length && !uploadedFiles.value.length) {
      await uploadSelectedFiles()
    }

    // 直接提交新字段（后端第一阶段已支持 summary + contentMarkdown）
    await store.submitResource({
      title: form.title,
      cat: form.cat,
      summary: form.summary,
      contentMarkdown: form.contentMarkdown,
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
  <section>
    <div class="page-title">
      <div>
        <h1>发布资源</h1>
        <p class="sub">附件为可选项。填写标题、分类、摘要和 Markdown 正文后即可提交审核。</p>
      </div>
    </div>
    <el-alert v-if="!store.logged" title="请先登录后发布资源。" type="warning" show-icon :closable="false" style="margin-bottom: 16px" />
    <el-card class="form-card" shadow="never">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item label="资源标题">
              <el-input v-model="form.title" placeholder="例如：Java 课程设计项目模板" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="资源分类">
              <el-select v-model="form.cat" class="full">
                <el-option v-for="category in store.categories" :key="category" :label="category" :value="category" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">

          </el-col>
          <el-col :span="24">
            <el-form-item label="附件（可选）">
              <el-upload
                v-model:file-list="selectedFiles"
                drag
                multiple
                action="#"
                :auto-upload="false"
                :show-file-list="false"
                class="upload-panel"
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">点击或拖拽文件到此处，支持多选批量上传</div>
              </el-upload>
            </el-form-item>

            <!-- 选中的待上传文件列表：支持逐个移除（提交前） -->
            <div v-if="selectedFiles.length" class="uploaded-list">
              <div
                v-for="(file, index) in selectedFiles"
                :key="file.uid ?? index"
                class="uploaded-row"
                style="display:flex; align-items:center; justify-content:space-between; gap:12px;"
              >
                <div style="flex:1; min-width:0; overflow:hidden;">
                  <b style="word-break:break-all;">{{ file.name }}</b>
                  <span v-if="file.size != null" class="sub">{{ file.size }} 字节</span>
                </div>
                <el-button
                  size="small"
                  type="danger"
                  text
                  :icon="Close"
                  @click="removeSelectedFile(index)"
                  title="移除此文件"
                />
              </div>
            </div>

            <!-- upload progress + per-file error feedback (minimal addition) -->
            <div v-if="uploading || uploadProgress > 0" style="margin: 8px 0 4px;">
              <el-progress :percentage="uploadProgress" :stroke-width="18" :text-inside="true" />
              <div style="font-size: 12px; color: #909399; margin-top: 2px;">文件上传中，请稍候…</div>
            </div>
            <div v-if="uploadErrors.length" style="margin: 4px 0; padding: 6px 8px; background: #fef0f0; color: #f56c6c; font-size: 12px; border-radius: 4px; line-height: 1.4;">
              验证/上传问题：{{ uploadErrors.join('； ') }}
            </div>
          </el-col>
          <el-col :span="24">
            <el-form-item label="资源摘要">
              <el-input 
                v-model="form.summary" 
                type="textarea" 
                :rows="2" 
                placeholder="一句话总结这个资源（用于卡片展示和搜索结果）" 
              />
            </el-form-item>
          </el-col>

          <el-col :span="24">
            <el-form-item label="Markdown 正文">
              <MarkdownEditor v-model="form.contentMarkdown" />
            </el-form-item>
          </el-col>
        </el-row>

        <div v-if="uploadedFiles.length" class="uploaded-list">
          <div
            v-for="(file, index) in uploadedFiles"
            :key="file.fileUrl || index"
            class="uploaded-row"
            style="display:flex; align-items:center; justify-content:space-between; gap:12px;"
          >
            <div style="flex:1; min-width:0; overflow:hidden;">
              <b style="word-break:break-all;">{{ file.originalName }}</b>
              <span class="sub">{{ file.fileType }} · {{ file.fileSize }} 字节</span>
            </div>
            <el-button
              size="small"
              type="danger"
              text
              :icon="Close"
              @click="removeUploadedFile(index)"
              title="移除该附件（提交前）"
            />
          </div>
        </div>

        <el-button type="primary" :loading="submitting || uploading" :disabled="!canSubmit" @click="submit">提交审核</el-button>
        <el-button @click="() => saveDraft(true)">保存草稿</el-button>
        <el-button @click="loadDraft">加载草稿</el-button>
        <el-button @click="clearDraft">清除草稿</el-button>
      </el-form>
    </el-card>
  </section>
</template>
