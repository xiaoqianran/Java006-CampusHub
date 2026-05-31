<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AdminLayout from '@/components/AdminLayout.vue'
import { useAppStore, type AdminLogItem } from '@/stores/app'

const store = useAppStore()

const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterAction = ref('')

const actionOptions = [
  { label: '全部', value: '' },
  { label: '资源审核', value: 'RESOURCE_AUDIT' },
  { label: '用户状态变更', value: 'USER_STATUS_CHANGE' },
  { label: '资源恢复', value: 'RESOURCE_RESTORE' },
  { label: '资源永久删除', value: 'RESOURCE_PERMANENT_DELETE' }
]

async function load(page = 1) {
  loading.value = true
  try {
    const data = await store.loadAdminLogs({
      page,
      size: pageSize.value,
      action: filterAction.value || undefined
    })
    total.value = data.total || 0
    currentPage.value = page
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载日志失败')
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  load(1)
}

function onPageChange(p: number) {
  load(p)
}

onMounted(() => {
  load(1)
})
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>操作审计日志</h1>
        <p class="sub">轻量级记录关键管理员操作（资源审核、用户启禁用、回收站恢复/删除等）。</p>
      </div>
    </div>

    <div class="toolbar" style="margin-bottom:12px;display:flex;gap:12px;align-items:center;flex-wrap:wrap;">
      <span>筛选动作：</span>
      <el-select v-model="filterAction" placeholder="全部" style="width:200px" @change="onFilterChange" clearable>
        <el-option v-for="opt in actionOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-button @click="load(1)">刷新</el-button>
      <span class="sub" style="margin-left:auto;">仅保留最近1000条（内存）</span>
    </div>

    <el-table :data="store.adminLogs" v-loading="loading" class="panel" stripe>
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ row.createTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作员ID" width="100">
        <template #default="{ row }">{{ row.operatorId }}</template>
      </el-table-column>
      <el-table-column label="动作" width="220">
        <template #default="{ row }">
          <el-tag :type="row.action.includes('AUDIT') ? 'primary' : row.action.includes('USER') ? 'warning' : row.action.includes('RESTORE') ? 'success' : 'danger'" size="small">
            {{ row.action }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="目标ID" width="100">
        <template #default="{ row }">{{ row.targetId ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="详情" min-width="200">
        <template #default="{ row }">{{ row.detail || '-' }}</template>
      </el-table-column>
    </el-table>

    <div style="margin-top:16px;display:flex;justify-content:flex-end;">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="onPageChange"
      />
    </div>

    <div v-if="!loading && !store.adminLogs.length" class="sub" style="margin-top:12px;">
      暂无操作日志。执行资源审核、用户启禁用或回收站操作后会自动记录。
    </div>
  </AdminLayout>
</template>
