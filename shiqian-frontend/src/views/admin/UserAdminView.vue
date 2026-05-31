<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminLayout from '@/components/AdminLayout.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore, type UserItem } from '@/stores/app'

const store = useAppStore()
const keyword = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    await store.loadUsers({ keyword: keyword.value.trim() })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用户加载失败')
  } finally {
    loading.value = false
  }
}

async function toggleStatus(row: UserItem) {
  const isDisable = row.status === '正常'
  const targetStatus: 0 | 1 = isDisable ? 0 : 1
  const actionText = isDisable ? '禁用' : '启用'

  try {
    await ElMessageBox.confirm(
      `确认${actionText}用户「${row.username || row.nickname || '该用户'}」吗？`,
      `${actionText}确认`,
      {
        type: isDisable ? 'warning' : 'info',
        confirmButtonText: actionText,
        cancelButtonText: '取消'
      }
    )
    await store.updateUserStatus(row.id, targetStatus, keyword.value.trim())
    ElMessage.success(`${actionText}成功`)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : `${actionText}失败`)
    }
  }
}

onMounted(load)
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>用户管理</h1>
        <p class="sub">统一管理学生与管理员账号，支持按关键词搜索并快速启禁用。</p>
      </div>
      <div class="toolbar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索用户名/昵称/邮箱/电话"
          style="width: 280px"
          @keyup.enter="load"
        />
        <el-button @click="load" :loading="loading">查询</el-button>
        <el-button @click="load" plain>刷新</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="store.users" class="panel" style="width: 100%">
      <el-table-column prop="username" label="用户名" min-width="140" />
      <el-table-column prop="nickname" label="昵称" min-width="140" />
      <el-table-column prop="email" label="邮箱" min-width="200" />
      <el-table-column prop="phone" label="电话" min-width="140" />

      <el-table-column label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="row.role === '管理员' ? 'danger' : 'info'">{{ row.role }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <StatusTag :status="row.status" />
        </template>
      </el-table-column>

      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button
            size="small"
            :type="row.status === '正常' ? 'danger' : 'success'"
            plain
            @click="toggleStatus(row)"
          >
            {{ row.status === '正常' ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && !store.users.length" description="暂无匹配的用户" />
  </AdminLayout>
</template>
