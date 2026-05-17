<template>
  <div class="empty-state">
    <component :is="icon" v-if="icon" class="w-12 h-12 mb-4 opacity-40" />
    <div class="font-medium text-lg">{{ title }}</div>
    <p class="text-sm max-w-xs mt-1 text-[#8a7155]">{{ description }}</p>
    <slot name="action" />
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
defineProps<{
  title: string
  description?: string
  icon?: Component
}>()
</script>