<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadRawFile, UploadUserFile } from 'element-plus'
import { Close, UploadFilled } from '@element-plus/icons-vue'
import { useAppStore, type UploadedFileItem, type ResourceAttachmentItem } from '@/stores/app'
import MarkdownEditor from '@/components/MarkdownEditor.vue'

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

const form = reactive({
  title: '',
  cat: '',
  summary: '',
  contentMarkdown: ''
})

const canSubmit = computed(() => {
  return Boolean(resourceId.value && form.title && form.cat && form.summary && form.contentMarkdown)
})

function fillForm() {
  const resource = store.getResource(resourceId.value)
  if (!resource) return false

  form.title = resource.title
  form.cat = resource.cat
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

async function uploadSelectedFiles() {
  const files = selectedFiles.value
    .map(item => item.raw)
    .filter((item): item is UploadRawFile => Boolean(item))

  if (!files.length) {
    throw new Error('请先选择附件')
  }

  uploading.value = true
  try {
    const newlyUploaded = await store.uploadFiles(files)
    // 支持添加更多：追加而非替换（区别于Publish的初次）
    uploadedFiles.value = [...uploadedFiles.value, ...newlyUploaded]
    selectedFiles.value = []
    ElMessage.success(`已上传 ${newlyUploaded.length} 个新附件`)
  } finally {
    uploading.value = false
  }
}

function removeSelectedFile(index: number) {
  selectedFiles.value.splice(index, 1)
}

function removeExistingAttachment(index: number) {
  existingAttachments.value.splice(index, 1)
}

function removeUploadedFile(index: number) {
  uploadedFiles.value.splice(index, 1)
}

async function submit() {
  if (!canSubmit.value) {
    ElMessage.warning('请补充标题、分类、摘要和 Markdown 正文')
    return
  }

  submitting.value = true
  try {
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
      cat: form.cat,
      summary: form.summary,
      contentMarkdown: form.contentMarkdown,
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
        <h1>编辑资源</h1>
        <p class="sub">修改标题、分类、摘要和正文后保存。资源状态保持不变，已驳回资源可在我的发布中重新提交审核。</p>
      </div>
    </div>

    <el-card v-loading="loading" class="form-card" shadow="never">
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
          <el-col :span="24">
            <el-form-item label="附件（多附件支持：可移除现有，添加新上传；保存时全量替换）">
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
                  <span v-if="file.size != null" class="sub">{{ file.size }} 字节</span>
                </div>
                <el-button size="small" type="danger" text :icon="Close" title="移除此文件" @click="removeSelectedFile(index)" />
              </div>
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

        <el-button type="primary" :loading="submitting || uploading" :disabled="!canSubmit" @click="submit">保存修改</el-button>
        <el-button @click="router.back()">取消</el-button>
      </el-form>
    </el-card>
  </section>
</template>
