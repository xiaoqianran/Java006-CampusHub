<template>
  <div class="max-w-3xl mx-auto px-6 py-10">
    <div class="mb-8">
      <div class="uppercase tracking-[2px] text-xs text-[#0f766e]">CONTRIBUTE TO THE COMMONS</div>
      <h1 class="text-4xl font-semibold tracking-tight">发布新资源</h1>
      <p class="text-[#5c4630] mt-1">你的笔记、报告、课件，将帮助无数后来的学弟学妹</p>
    </div>

    <!-- 步骤条 -->
    <el-steps :active="currentStep" finish-status="success" class="mb-8">
      <el-step title="基本信息" />
      <el-step title="选择分类" />
      <el-step title="上传文件" />
      <el-step title="确认提交" />
    </el-steps>

    <!-- Step 1: 基本信息 -->
    <div v-if="currentStep === 0" class="shiqian-card p-8">
      <h3 class="font-semibold mb-5 text-lg">1. 资源基本信息</h3>
      <el-form label-position="top" :model="form">
        <el-form-item label="资源标题（必填，≤200字）">
          <el-input v-model="form.title" size="large" placeholder="例如：数据结构期末复习笔记（含思维导图）" />
        </el-form-item>
        <el-form-item label="详细描述（可选，≤1000字）">
          <el-input v-model="form.description" type="textarea" :rows="5" placeholder="课程、学期、亮点、适用人群..." />
        </el-form-item>
      </el-form>
      <div class="flex justify-end">
        <el-button type="primary" size="large" @click="nextStep" :disabled="!form.title">下一步</el-button>
      </div>
    </div>

    <!-- Step 2: 分类 -->
    <div v-else-if="currentStep === 1" class="shiqian-card p-8">
      <h3 class="font-semibold mb-5 text-lg">2. 选择所属分类</h3>
      <el-cascader
        v-model="form.categoryId"
        :options="categoryStore.getCascaderOptions()"
        :props="{ expandTrigger: 'hover', checkStrictly: true }"
        placeholder="请选择分类（支持搜索）"
        size="large"
        class="w-full"
        filterable
        clearable
      />
      <div class="text-xs text-[#8a7155] mt-3">分类数据实时来自后端（Redis 缓存）</div>

      <div class="flex justify-between mt-8">
        <el-button @click="prevStep" size="large">上一步</el-button>
        <el-button type="primary" size="large" @click="nextStep" :disabled="!form.categoryId">下一步</el-button>
      </div>
    </div>

    <!-- Step 3: 文件上传（核心） -->
    <div v-else-if="currentStep === 2" class="shiqian-card p-8">
      <h3 class="font-semibold mb-5 text-lg">3. 上传资源文件</h3>
      <FileDropzone 
        @file-ready="onFileReady" 
        @file-removed="onFileRemoved" 
      />

      <div class="flex justify-between mt-8">
        <el-button @click="prevStep" size="large">上一步</el-button>
        <el-button type="primary" size="large" @click="nextStep" :disabled="!form.fileUrl">下一步</el-button>
      </div>
    </div>

    <!-- Step 4: 预览 & 提交 -->
    <div v-else class="shiqian-card p-8">
      <h3 class="font-semibold mb-5 text-lg">4. 确认并提交审核</h3>

      <div class="bg-[#f8f5f0] rounded-2xl p-6 text-sm space-y-2">
        <div><span class="text-[#8a7155] w-20 inline-block">标题：</span> {{ form.title }}</div>
        <div><span class="text-[#8a7155] w-20 inline-block">分类：</span> {{ categoryName }}</div>
        <div><span class="text-[#8a7155] w-20 inline-block">文件：</span> {{ form.fileName || form.fileUrl }}</div>
        <div><span class="text-[#8a7155] w-20 inline-block">大小：</span> {{ formatSize(form.fileSize) }}</div>
      </div>

      <div class="mt-6 p-4 bg-emerald-50 text-emerald-800 rounded-2xl text-sm border border-emerald-100">
        提交后资源将进入<strong>待审核</strong>状态（status=0）。通过后自动出现在公开列表中，感谢你的贡献！
      </div>

      <div class="flex justify-between mt-8">
        <el-button @click="prevStep" size="large">返回修改</el-button>
        <el-button type="primary" size="large" @click="submitResource" :loading="submitting">
          确认提交，进入审核队列
        </el-button>
      </div>
    </div>

    <!-- 成功提示 -->
    <div v-if="submitSuccess" class="mt-6 text-center shiqian-card p-8 bg-emerald-50 border-emerald-200">
      <CheckCircle class="mx-auto text-emerald-600 w-12 h-12 mb-4" />
      <div class="text-2xl font-semibold text-emerald-800">提交成功！</div>
      <p class="text-emerald-700 mt-2">资源已进入审核队列，管理员通常在 24 小时内完成审核。</p>
      <div class="mt-6 flex gap-3 justify-center">
        <el-button @click="viewMyResource" type="primary">查看我的资源</el-button>
        <el-button @click="resetForm">继续发布新资源</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CheckCircle } from 'lucide-vue-next'
import { resourceApi } from '../api/resource'
import { useCategoryStore } from '../stores/category'
import { useAuthStore } from '../stores/auth'
import FileDropzone from '../components/FileDropzone.vue'

const router = useRouter()
const categoryStore = useCategoryStore()
const authStore = useAuthStore()

const currentStep = ref(0)
const submitting = ref(false)
const submitSuccess = ref(false)

const form = reactive({
  title: '',
  description: '',
  categoryId: 0 as number | undefined,
  fileUrl: '',
  fileSize: 0,
  fileType: '',
  fileName: ''
})

const categoryName = computed(() => categoryStore.getCategoryName(form.categoryId))

onMounted(() => {
  if (!authStore.isAuthenticated) {
    ElMessage.warning('请先登录后再发布资源')
    router.push('/login?redirect=/publish')
  }
  categoryStore.loadTree()
})

function nextStep() {
  if (currentStep.value < 3) currentStep.value++
}

function prevStep() {
  if (currentStep.value > 0) currentStep.value--
}

function onFileReady(payload: any) {
  form.fileUrl = payload.fileUrl
  form.fileSize = payload.fileSize
  form.fileType = payload.fileType
  form.fileName = payload.fileName
}

function onFileRemoved() {
  form.fileUrl = ''
  form.fileSize = 0
  form.fileType = ''
  form.fileName = ''
}

function formatSize(size: number) {
  if (!size) return '0 B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(2) + ' MB'
}

async function submitResource() {
  if (!form.title || !form.categoryId || !form.fileUrl) {
    ElMessage.error('请完整填写所有必填项')
    return
  }

  submitting.value = true
  try {
    await resourceApi.createResource({
      title: form.title,
      description: form.description || undefined,
      categoryId: form.categoryId,
      fileUrl: form.fileUrl,
      fileSize: form.fileSize,
      fileType: form.fileType
    })

    submitSuccess.value = true
    ElMessage.success('资源提交成功！等待管理员审核')
  } catch (e: any) {
    ElMessage.error(e.message || '提交失败（可能包含敏感词）')
  } finally {
    submitting.value = false
  }
}

function viewMyResource() {
  router.push('/profile')
}

function resetForm() {
  Object.assign(form, { title: '', description: '', categoryId: undefined, fileUrl: '', fileSize: 0, fileType: '', fileName: '' })
  currentStep.value = 0
  submitSuccess.value = false
}
</script>