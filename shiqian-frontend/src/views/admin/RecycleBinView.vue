<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AdminLayout from '@/components/AdminLayout.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const keyword = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    await store.loadRecycleResources({ keyword: keyword.value.trim() })
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>回收站</h1>
        <p class="sub">管理员可查看已删除的资源。</p>
      </div>
      <div class="toolbar">
        <el-input v-model="keyword" clearable placeholder="搜索回收站" style="width: 260px" @keyup.enter="load" />
        <el-button @click="load">查询</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="store.recycleResources" class="panel">
      <el-table-column label="资源" min-width="280">
        <template #default="{ row }">
          <b>{{ row.title }}</b>
          <div class="sub">用户 {{ row.userId || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="cat" label="分类" width="130" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }"><StatusTag :status="row.status" /></template>
      </el-table-column>
      <el-table-column prop="downloads" label="下载" width="100" />
    </el-table>
  </AdminLayout>
</template>
