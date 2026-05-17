<template>
  <div>
    <!-- Dropzone 区域 -->
    <div
      v-if="!selectedFile"
      class="dropzone"
      :class="{ dragover: isDragging }"
      @dragenter.prevent="isDragging = true"
      @dragover.prevent="isDragging = true"
      @dragleave.prevent="isDragging = false"
      @drop.prevent="handleDrop"
      @click="triggerFileInput"
    >
      <div class="flex flex-col items-center justify-center">
        <UploadCloud class="w-12 h-12 text-[#0f766e] mb-4" />
        <div class="text-xl font-medium text-[#172026]">拖拽文件到此处，或点击选择</div>
        <div class="text-sm text-[#5c4630] mt-2">支持 PDF、PPT、DOC、ZIP、MD、图片等校园常用格式</div>
        <div class="text-xs text-[#8a7155] mt-1">最大 100MB · 上传后自动生成校园云地址</div>
      </div>

      <input
        ref="fileInput"
        type="file"
        class="hidden"
        :accept="acceptTypes"
        @change="handleFileSelect"
      />
    </div>

    <!-- 已选择文件 + 上传模拟 -->
    <div v-else class="shiqian-card p-6">
      <div class="flex gap-4">
        <!-- 文件图标/预览 -->
        <div class="w-16 h-16 flex-shrink-0 rounded-2xl bg-[#f8f5f0] flex items-center justify-center text-4xl">
          {{ fileIcon }}
        </div>

        <div class="flex-1 min-w-0">
          <div class="font-semibold text-lg truncate">{{ selectedFile.name }}</div>
          <div class="text-sm text-[#5c4630]">{{ formatSize(selectedFile.size) }} · {{ selectedFile.type || '未知类型' }}</div>

          <!-- 进度条 -->
          <div v-if="uploading" class="mt-3">
            <div class="flex justify-between text-xs mb-1">
              <span class="text-[#0f766e]">正在上传到 时迁校园云存储...</span>
              <span class="font-mono">{{ uploadProgress }}%</span>
            </div>
            <el-progress :percentage="uploadProgress" :show-text="false" :stroke-width="6" color="#0f766e" />
          </div>

          <!-- 完成状态 -->
          <div v-else-if="uploadComplete" class="mt-2 flex items-center gap-2 text-emerald-700 text-sm">
            <CheckCircle class="w-4 h-4" />
            <span>上传成功 · 已加密存储 · 仅审核通过后公开</span>
          </div>
        </div>

        <button @click="removeFile" class="text-[#8a7155] hover:text-rose-600 p-1 self-start">
          <X class="w-5 h-5" />
        </button>
      </div>

      <!-- 手动 URL 高级模式（给极客用户） -->
      <div v-if="uploadComplete" class="mt-4 pt-4 border-t text-xs">
        <div class="text-[#8a7155] mb-1">高级：手动指定文件 URL（可选）</div>
        <el-input v-model="manualUrl" placeholder="https://oss.shiqian.edu/res/xxx.pdf" size="small" @input="updateManual" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { UploadCloud, CheckCircle, X } from 'lucide-vue-next'
import { ElProgress, ElMessage } from 'element-plus'

const emit = defineEmits<{
  (e: 'fileReady', payload: { fileUrl: string; fileSize: number; fileType: string; fileName: string }): void
  (e: 'fileRemoved'): void
}>()

const fileInput = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadComplete = ref(false)
const manualUrl = ref('')

const acceptTypes = '.pdf,.ppt,.pptx,.doc,.docx,.xls,.xlsx,.zip,.rar,.md,.txt,.png,.jpg,.jpeg,.gif'

const fileIcon = computed(() => {
  const name = selectedFile.value?.name.toLowerCase() || ''
  if (name.endsWith('.pdf')) return '📕'
  if (name.endsWith('.ppt') || name.endsWith('.pptx')) return '📊'
  if (name.endsWith('.doc') || name.endsWith('.docx')) return '📄'
  if (name.endsWith('.zip') || name.endsWith('.rar')) return '📦'
  if (name.match(/\.(png|jpg|jpeg|gif)$/)) return '🖼️'
  if (name.endsWith('.md')) return '📝'
  return '📁'
})

function formatSize(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

function triggerFileInput() {
  fileInput.value?.click()
}

function handleFileSelect(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files?.[0]) {
    processFile(target.files[0])
  }
}

function handleDrop(e: DragEvent) {
  isDragging.value = false
  if (e.dataTransfer?.files?.[0]) {
    processFile(e.dataTransfer.files[0])
  }
}

function processFile(file: File) {
  // 校验
  const maxSize = 100 * 1024 * 1024 // 100MB
  if (file.size > maxSize) {
    ElMessage.error('文件过大（演示环境限制 100MB）')
    return
  }

  const allowed = ['pdf', 'ppt', 'pptx', 'doc', 'docx', 'xls', 'xlsx', 'zip', 'rar', 'md', 'txt', 'png', 'jpg', 'jpeg', 'gif']
  const ext = file.name.split('.').pop()?.toLowerCase() || ''
  if (!allowed.includes(ext)) {
    ElMessage.warning('暂不支持该格式（演示版）')
    return
  }

  selectedFile.value = file
  uploading.value = true
  uploadProgress.value = 0
  uploadComplete.value = false
  manualUrl.value = ''

  // 模拟真实上传进度（0.8~1.8秒）
  const duration = 800 + Math.random() * 1000
  const interval = setInterval(() => {
    uploadProgress.value += Math.ceil(Math.random() * 18) + 7
    if (uploadProgress.value >= 100) {
      uploadProgress.value = 100
      clearInterval(interval)
      finishUpload(file)
    }
  }, 90)
}

function finishUpload(file: File) {
  uploading.value = false
  uploadComplete.value = true

  // 生成可直接使用的校园云 URL（生产环境可换成真实 OSS 返回值）
  const timestamp = Date.now()
  const safeName = file.name.replace(/[^a-zA-Z0-9._-]/g, '_')
  const generatedUrl = `https://oss.shiqian-campus.edu/res/${timestamp}-${safeName}`

  // 通知父组件文件已就绪
  emit('fileReady', {
    fileUrl: generatedUrl,
    fileSize: file.size,
    fileType: file.type || `application/${file.name.split('.').pop()}`,
    fileName: file.name
  })
}

function updateManual() {
  if (selectedFile.value && manualUrl.value) {
    emit('fileReady', {
      fileUrl: manualUrl.value,
      fileSize: selectedFile.value.size,
      fileType: selectedFile.value.type || 'application/octet-stream',
      fileName: selectedFile.value.name
    })
  }
}

function removeFile() {
  selectedFile.value = null
  uploading.value = false
  uploadComplete.value = false
  uploadProgress.value = 0
  manualUrl.value = ''
  emit('fileRemoved')
  // 重置 input
  if (fileInput.value) fileInput.value.value = ''
}
</script>