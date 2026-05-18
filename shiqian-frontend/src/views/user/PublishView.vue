<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRawFile, UploadUserFile } from 'element-plus'
import { useRouter } from 'vue-router'
import { UploadFilled } from '@element-plus/icons-vue'
import { useAppStore, type UploadedFileItem } from '@/stores/app'
import MarkdownEditor from '@/components/MarkdownEditor.vue'

const router = useRouter()
const store = useAppStore()
const submitting = ref(false)
const uploading = ref(false)
const selectedFiles = ref<UploadUserFile[]>([])
const uploadedFiles = ref<UploadedFileItem[]>([])

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
})

onMounted(() => {
  store.loadCategories().catch(() => undefined)
  const draft = localStorage.getItem('shiqian_publish_draft')
  if (draft) {
    Object.assign(form, JSON.parse(draft))
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
    uploadedFiles.value = await store.uploadFiles(files)
    ElMessage.success(`已上传 ${uploadedFiles.value.length} 个附件`)
  } finally {
    uploading.value = false
  }
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
    localStorage.removeItem('shiqian_publish_draft')
    ElMessage.success('资源已提交审核')
    router.push('/mine')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交失败')
  } finally {
    submitting.value = false
  }
}

function saveDraft() {
  localStorage.setItem('shiqian_publish_draft', JSON.stringify(form))
  ElMessage.success('草稿已保存')
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
                class="upload-panel"
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">点击或拖拽文件到此处，支持多选批量上传</div>
              </el-upload>
            </el-form-item>
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
          <div v-for="file in uploadedFiles" :key="file.fileUrl" class="uploaded-row">
            <b>{{ file.originalName }}</b>
            <span class="sub">{{ file.fileType }} · {{ file.fileSize }} 字节</span>
          </div>
        </div>

        <el-button type="primary" :loading="submitting || uploading" :disabled="!canSubmit" @click="submit">提交审核</el-button>
        <el-button @click="saveDraft">保存草稿</el-button>
      </el-form>
    </el-card>
  </section>
</template>
