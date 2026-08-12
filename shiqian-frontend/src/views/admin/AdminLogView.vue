<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AdminLayout from '@/components/AdminLayout.vue'
import { useAdminStore } from '@/stores/admin'
import type { AdminLogItem } from '@/stores/types'

const admin = useAdminStore()

const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterAction = ref('')
const filterOperatorId = ref<number>()
const filterTime = ref<[Date, Date]>()

const startTime = computed(() => filterTime.value?.[0]?.toISOString())
const endTime = computed(() => filterTime.value?.[1]?.toISOString())

const actionOptions = [
  { label: '全部', value: '' },
  { label: '审核通过', value: 'RESOURCE_APPROVE' },
  { label: '退回修改', value: 'RESOURCE_NEEDS_CHANGES' },
  { label: '拒绝资源', value: 'RESOURCE_REJECT' },
  { label: '下架资源', value: 'RESOURCE_TAKE_DOWN' },
  { label: '用户状态变更', value: 'USER_STATUS_CHANGE' },
  { label: '资源恢复', value: 'RESOURCE_RESTORE' },
  { label: '资源永久删除', value: 'RESOURCE_PERMANENT_DELETE' }
]

async function load(page = 1) {
  loading.value = true
  try {
    const data = await admin.loadAdminLogs({
      page,
      size: pageSize.value,
      action: filterAction.value || undefined,
      operatorId: filterOperatorId.value,
      startTime: startTime.value,
      endTime: endTime.value
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
        <p class="sub">持久化记录关键管理员操作，服务重启后仍可追溯。</p>
      </div>
    </div>

    <div class="toolbar" style="margin-bottom:12px;display:flex;gap:12px;align-items:center;flex-wrap:wrap;">
      <span>筛选动作：</span>
      <el-select v-model="filterAction" placeholder="全部" style="width:200px" @change="onFilterChange" clearable>
        <el-option v-for="opt in actionOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-input-number
        v-model="filterOperatorId"
        :min="1"
        :controls="false"
        placeholder="操作员ID"
        style="width:140px"
        @change="onFilterChange"
      />
      <el-date-picker
        v-model="filterTime"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        @change="onFilterChange"
      />
      <el-button @click="load(1)">刷新</el-button>
      <span class="sub" style="margin-left:auto;">数据库持久化 · 支持分页检索</span>
    </div>

    <el-table :data="admin.adminLogs" v-loading="loading" class="panel" stripe>
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ row.createTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作员" width="150">
        <template #default="{ row }">{{ row.operatorName || `用户#${row.operatorId}` }}</template>
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
      <el-table-column label="结果" width="100">
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ row.result || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="100">
        <template #default="{ row }">{{ row.durationMs == null ? '-' : `${row.durationMs} ms` }}</template>
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

    <div v-if="!loading && !admin.adminLogs.length" class="sub" style="margin-top:12px;">
      暂无操作日志。执行内容审核、用户启禁用或回收站操作后会自动记录。
    </div>
  </AdminLayout>
</template>
