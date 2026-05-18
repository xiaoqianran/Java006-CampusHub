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

function handleUploadImg(files: File[], callback: (urls: string[]) => void) {
  // TODO: 后续接入真实图片上传接口
  // 当前先使用本地预览（仅测试用）
  const urls = files.map(file => URL.createObjectURL(file))
  callback(urls)
}
</script>
