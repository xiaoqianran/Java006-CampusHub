<script setup lang="ts">
import type { ResourceStatus } from '@/stores/app'

defineProps<{ status: ResourceStatus | '正常' | '禁用' }>()
</script>

<template>
  <el-tag v-if="status === '已发布' || status === '正常'" type="success">{{ status }}</el-tag>
  <el-tag v-else-if="status === '待审核'" type="warning">{{ status }}</el-tag>
  <el-tag v-else type="danger">{{ status }}</el-tag>
</template>
