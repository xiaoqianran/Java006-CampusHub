<script setup lang="ts">
import AdminLayout from '@/components/AdminLayout.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>用户管理</h1>
        <p class="sub">统一管理学生、教师、社团与管理员账号。</p>
      </div>
    </div>
    <el-table :data="store.users" class="panel">
      <el-table-column prop="name" label="用户" min-width="160" />
      <el-table-column prop="role" label="角色" width="120" />
      <el-table-column prop="email" label="邮箱" min-width="220" />
      <el-table-column label="状态" width="120"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>
      <el-table-column label="操作" width="120"><el-button size="small">查看</el-button></el-table-column>
    </el-table>
  </AdminLayout>
</template>
