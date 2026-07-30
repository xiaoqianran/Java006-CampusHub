<script setup lang="ts">
import { computed } from 'vue'
import MarkdownPreview from '@/components/MarkdownPreview.vue'

defineOptions({ name: 'MarkdownLiveEditor' })

const props = defineProps<{
  modelValue: string
  placeholder?: string
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
}>()

const content = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})
</script>

<template>
  <div class="live-editor">
    <section class="editor-pane">
      <div class="pane-heading">
        <b>Markdown 写作</b>
        <span>支持普通文字、标题、列表、表格和代码块</span>
      </div>
      <el-input
        v-model="content"
        type="textarea"
        :placeholder="placeholder || '写下正文，右侧会同步显示最终效果。'"
        resize="none"
        class="live-input"
      />
    </section>

    <section class="preview-pane">
      <div class="pane-heading">
        <b>实时预览</b>
        <span>输入内容后立即更新</span>
      </div>
      <MarkdownPreview
        v-if="content.trim()"
        :model-value="content"
        editor-id="publish-live-preview"
        class="live-preview"
      />
      <div v-else class="empty-preview">预览内容会显示在这里</div>
    </section>
  </div>
</template>

<style scoped>
.live-editor {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
}

.editor-pane,
.preview-pane {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  background: var(--el-bg-color);
}

.pane-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 46px;
  padding: 0 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.pane-heading span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-align: right;
}

.live-input :deep(textarea) {
  min-height: 380px !important;
  padding: 16px;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  line-height: 1.75;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.live-preview {
  min-height: 380px;
  max-height: 560px;
  border: 0;
  border-radius: 0;
}

.empty-preview {
  display: grid;
  min-height: 380px;
  place-items: center;
  color: var(--el-text-color-placeholder);
}

@media (max-width: 820px) {
  .live-editor {
    grid-template-columns: 1fr;
  }

  .live-input :deep(textarea),
  .live-preview,
  .empty-preview {
    min-height: 280px !important;
  }
}
</style>
