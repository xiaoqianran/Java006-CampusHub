<template>
  <MdEditor
    v-model="modelValue"
    :theme="theme"
    :preview="true"
    :toolbars="toolbars"
    :auto-focus="false"
    placeholder="请在这里输入 Markdown 内容，支持标题、列表、代码块、图片等..."
    @on-upload-img="handleUploadImg"
    style="height: 420px; border: 1px solid var(--line); border-radius: 12px;"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { MdEditor, ToolbarNames } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { ElMessage } from 'element-plus'
import { useAppStore, type UploadedFileItem } from '@/stores/app'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const modelValue = computed({
  get: () => props.modelValue,
  set: (val: string) => emit('update:modelValue', val)
})

const store = useAppStore()

// 根据系统当前主题自动切换编辑器主题
const theme = computed(() => {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
})

// 常用工具栏配置
const toolbars: ToolbarNames[] = [
  'bold', 'underline', 'italic', 'strikeThrough', '-',
  'title', 'sub', 'sup', 'quote', 'unorderedList', 'orderedList', '-',
  'code', 'codeRow', 'link', 'image', 'table', '-',
  'revoke', 'next', 'prettier', 'pageFullscreen', 'fullscreen'
]

async function handleUploadImg(files: File[], callback: (urls: string[]) => void) {
  if (!files?.length) {
    callback([])
    return
  }

  // 使用现有 store.uploadFiles 上传到 /api/resource/files，返回远程 fileUrl（而非 blob）
  try {
    const uploaded: UploadedFileItem[] = await store.uploadFiles(files)
    const urls = uploaded.map(item => item.fileUrl)
    callback(urls)
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '图片上传失败'
    ElMessage.error(msg)
    // 必须回调，否则 MdEditor 上传 UI 会卡住；优雅降级
    callback([])
  }
}
</script>
