<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()

// 状态过滤（本地，复用 store.myResources 计算属性）
const statusFilter = ref<'全部' | '待审核' | '已发布' | '已驳回'>('全部')
const filteredMyResources = computed(() =>
  statusFilter.value === '全部'
    ? store.myResources
    : store.myResources.filter(r => r.status === statusFilter.value)
)

onMounted(() => {
  if (!store.logged) return
  store.loadMyResources().catch(error => {
    ElMessage.error(error instanceof Error ? error.message : '我的发布加载失败')
  })
})

async function remove(id: number) {
  await ElMessageBox.confirm('确定删除这条发布记录吗？', '删除确认', { type: 'warning' })
  try {
    await store.removeMyResource(id)
    ElMessage.success('已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

async function resubmit(id: number) {
  try {
    await ElMessageBox.confirm('确认重新提交这条资源进入审核吗？', '重新提交', { type: 'warning' })
    await store.resubmitResource(id)
    ElMessage.success('已重新提交审核')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '重新提交失败')
    }
  }
}

const profileForm = ref({
  nickname: store.currentUser?.nickname || '',
  email: store.currentUser?.email || '',
  phone: store.currentUser?.phone || ''
})

async function saveProfile() {
  try {
    await store.updateProfile(profileForm.value)
    ElMessage.success('资料已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新失败')
  }
}
</script>

<template>
  <section>
    <div class="page-title">
      <div>
        <h1>我的发布</h1>
        <p class="sub">显示待审核、已发布、已驳回三种状态，已驳回资源可重新提交审核。</p>
      </div>
    </div>

    <!-- 快速个人资料编辑 -->
    <el-card class="panel" style="margin-bottom: 16px;">
      <template #header>
        <div style="display:flex; align-items:center; justify-content:space-between; gap:12px;">
          <span>个人资料（快速编辑）</span>
          <el-button size="small" @click="$router.push('/profile')">完整编辑（头像预览）</el-button>
        </div>
      </template>
      <el-form :model="profileForm" inline>
        <el-form-item label="昵称">
          <el-input v-model="profileForm.nickname" style="width:160px" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="profileForm.email" style="width:200px" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="profileForm.phone" style="width:160px" />
        </el-form-item>
        <el-button type="primary" size="small" @click="saveProfile">保存资料</el-button>
      </el-form>
    </el-card>

    <!-- 状态过滤器：简单 tabs 风格，基于本地 ref + 复用 store.myResources -->
    <div style="display:flex;gap:12px;align-items:center;margin-bottom:12px;flex-wrap:wrap;">
      <el-radio-group v-model="statusFilter">
        <el-radio-button label="全部">全部</el-radio-button>
        <el-radio-button label="待审核">待审核</el-radio-button>
        <el-radio-button label="已发布">已发布</el-radio-button>
        <el-radio-button label="已驳回">已驳回</el-radio-button>
      </el-radio-group>
      <span class="sub">共 {{ filteredMyResources.length }} 条（含附件数、浏览/下载统计）</span>
    </div>

    <el-table :data="filteredMyResources" class="panel" style="width: 100%">
      <!-- 我的发布列表（我的资源作者即当前用户，store 统一使用后端数据；现支持状态过滤 + 附件/统计可视化） -->
      <el-table-column label="资源" min-width="280">
        <template #default="{ row }">
          <b>{{ row.title }}</b>
          <div class="sub">{{ row.desc }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="cat" label="分类" width="130" />
      <el-table-column label="状态" width="120"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>
      <el-table-column label="附件" width="80" align="center">
        <template #default="{ row }">📎 {{ (row.attachments && row.attachments.length) || 0 }}</template>
      </el-table-column>
      <el-table-column label="数据" width="170"><template #default="{ row }">{{ row.views }} 浏览 / {{ row.downloads }} 下载</template></el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button size="small" @click="$router.push(`/detail/${row.id}`)">查看</el-button>
          <el-button v-if="row.status === '已驳回'" size="small" type="primary" plain @click="resubmit(row.id)">重新提交</el-button>
          <el-button size="small" type="danger" plain @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
