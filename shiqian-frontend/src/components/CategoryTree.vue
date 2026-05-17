<template>
  <div class="text-sm">
    <div
      v-for="node in tree"
      :key="node.id"
      class="mb-0.5"
    >
      <div
        @click="select(node)"
        class="flex items-center gap-2 px-3 py-2 rounded-2xl cursor-pointer transition-all"
        :class="[
          selectedId === node.id 
            ? 'bg-[#0f766e] text-white font-medium' 
            : 'hover:bg-[#f0f9f7] text-[#172026]'
        ]"
      >
        <span class="flex-1 truncate">{{ node.name }}</span>
        <span v-if="node.children?.length" class="text-xs opacity-60">→</span>
      </div>

      <!-- 子分类 -->
      <div v-if="node.children?.length && expanded.has(node.id)" class="ml-4 mt-0.5 border-l border-[#e5e0d8] pl-3">
        <CategoryTree 
          :tree="node.children" 
          :selected-id="selectedId" 
          @select="$emit('select', $event)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { Category } from '../types/resource'

const props = defineProps<{
  tree: Category[]
  selectedId?: number
}>()

const emit = defineEmits(['select'])

const expanded = ref(new Set<number>())

function select(node: Category) {
  emit('select', node)
  // 自动展开有子类的
  if (node.children?.length) {
    if (expanded.value.has(node.id)) {
      expanded.value.delete(node.id)
    } else {
      expanded.value.add(node.id)
    }
  }
}

// 默认展开第一级
watch(() => props.tree, (t) => {
  if (t.length) {
    t.forEach(n => expanded.value.add(n.id))
  }
}, { immediate: true })
</script>