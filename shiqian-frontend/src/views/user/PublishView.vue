<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRawFile, UploadUserFile } from 'element-plus'
import { useRouter } from 'vue-router'
import { UploadFilled } from '@element-plus/icons-vue'
import { useAppStore, type UploadedFileItem } from '@/stores/app'

const router = useRouter()
const store = useAppStore()
const submitting = ref(false)
const uploading = ref(false)
const selectedFiles = ref<UploadUserFile[]>([])
const uploadedFiles = ref<UploadedFileItem[]>([])

const form = reactive({
  title: '',
  cat: '计算机科学',
  type: '',
  desc: ''
})

const canSubmit = computed(() => Boolean(form.title && form.cat && form.desc))

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
    ElMessage.warning('请补充标题、分类和简介')
    return
  }

  submitting.value = true
  try {
    if (selectedFiles.value.length && !uploadedFiles.value.length) {
      await uploadSelectedFiles()
    }
    await store.submitResource({
      title: form.title,
      cat: form.cat,
      type: form.type,
      desc: form.desc,
      files: uploadedFiles.value
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
        <p class="sub">附件为可选项；填写标题、分类和简介后即可提交审核。</p>
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
            <el-form-item label="资源类型">
              <el-input v-model="form.type" placeholder="不填时按附件类型自动识别" />
            </el-form-item>
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
            <el-form-item label="资源简介">
              <el-input v-model="form.desc" type="textarea" :rows="5" placeholder="请说明适用课程、内容范围及使用方法&#10;例如：数据结构课程 · 红黑树可视化实现 · 支持直接导入 IDEA 运行" />
              <div class="sub" style="margin-top: 4px; font-size: 12px;">
                搜索会覆盖标题、简介和文件类型，建议写得清晰具体
              </div>
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
