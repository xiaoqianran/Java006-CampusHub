<template>
  <div class="markdown-preview-panel" :data-theme="theme">
    <MdPreview
      :model-value="modelValue"
      :theme="theme"
      :editor-id="editorId || 'markdown-preview'"
      :preview-theme="previewTheme"
      :html="false"
      style="background: transparent; padding: 0;"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { useUiStore } from '@/stores/ui'

const props = defineProps<{
  modelValue: string
  editorId?: string
  previewTheme?: string
}>()

const ui = useUiStore()
const theme = computed(() => (ui.isDark ? 'dark' : 'light'))
</script>

<style scoped>
.markdown-preview-panel {
  padding: 16px 20px;
  border-radius: 8px;
  border: 1px solid var(--line, #e5e7eb);
  background: var(--bg-card, #ffffff);
  color: var(--text-primary, #111827);
  min-height: 120px;
  line-height: 1.7;
  overflow: auto;
}

/* 深色模式适配 - 防止闪白 */
.markdown-preview-panel[data-theme="dark"] {
  background: var(--bg-card-dark, #1f2937);
  border-color: var(--line-dark, #374151);
  color: var(--text-primary-dark, #f3f4f6);
}

/* 代码块、引用块等在深色下的稳定样式 */
.markdown-preview-panel :deep(pre),
.markdown-preview-panel :deep(code) {
  background: var(--code-bg, #f3f4f6);
  border-radius: 6px;
}

.markdown-preview-panel[data-theme="dark"] :deep(pre),
.markdown-preview-panel[data-theme="dark"] :deep(code) {
  background: var(--code-bg-dark, #111827);
  color: #e5e7eb;
}

.markdown-preview-panel :deep(blockquote) {
  border-left: 4px solid #6366f1;
  margin: 12px 0;
  padding-left: 16px;
  color: #4b5563;
}

.markdown-preview-panel[data-theme="dark"] :deep(blockquote) {
  border-left-color: #818cf8;
  color: #9ca3af;
}

/* 表格、链接等基础美化 */
.markdown-preview-panel :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}

.markdown-preview-panel :deep(th),
.markdown-preview-panel :deep(td) {
  border: 1px solid var(--line, #e5e7eb);
  padding: 8px 12px;
}

.markdown-preview-panel[data-theme="dark"] :deep(th),
.markdown-preview-panel[data-theme="dark"] :deep(td) {
  border-color: #374151;
}
</style>
