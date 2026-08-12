<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminLayout from '@/components/AdminLayout.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAdminStore } from '@/stores/admin'
import type { UserItem } from '@/stores/types'

const admin = useAdminStore()
const keyword = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    await admin.loadUsers({ keyword: keyword.value.trim() })
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
    await admin.updateUserStatus(row.id, targetStatus, keyword.value.trim())
    await admin.recordAdminLog('USER_STATUS_CHANGE', row.id, `${actionText}用户 ${row.username || row.nickname || row.id}`)
    ElMessage.success(`${actionText}成功`)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : `${actionText}失败`)
    }
  }
}

async function changeRole(row: UserItem, targetRole: 'USER' | 'ADMIN') {
  const isPromote = targetRole === 'ADMIN'
  const actionText = isPromote ? '设为管理员' : '设为学生'
  const currentRoleText = row.role

  try {
    await ElMessageBox.confirm(
      `确认将用户「${row.username || row.nickname || '该用户'}」的角色从「${currentRoleText}」${actionText}吗？`,
      '角色变更确认',
      {
        type: 'warning',
        confirmButtonText: actionText,
        cancelButtonText: '取消'
      }
    )
    await admin.updateUserRole(row.id, targetRole, keyword.value.trim())
    await admin.recordAdminLog('USER_ROLE_CHANGE', row.id, `${actionText} ${row.username || row.nickname || row.id}`)
    ElMessage.success('角色修改成功')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '角色修改失败')
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
        <p class="sub">统一管理学生与管理员账号，支持按关键词搜索、启禁用及角色调整（USER/ADMIN）。</p>
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

    <el-table v-loading="loading" :data="admin.users" class="panel" style="width: 100%">
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

      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button
            size="small"
            :type="row.status === '正常' ? 'danger' : 'success'"
            plain
            @click="toggleStatus(row)"
          >
            {{ row.status === '正常' ? '禁用' : '启用' }}
          </el-button>
          <el-button
            v-if="row.role !== '管理员'"
            size="small"
            type="primary"
            plain
            style="margin-left: 4px"
            @click="changeRole(row, 'ADMIN')"
          >
            设为管理员
          </el-button>
          <el-button
            v-if="row.role !== '学生'"
            size="small"
            type="info"
            plain
            style="margin-left: 4px"
            @click="changeRole(row, 'USER')"
          >
            设为学生
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && !admin.users.length" description="暂无匹配的用户" />
  </AdminLayout>
</template>
