<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminLayout from '@/components/AdminLayout.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  CONTENT_SCENES,
  contentSceneLabel,
  useAppStore,
  type ContentSceneFilter
} from '@/stores/app'

const store = useAppStore()

const searchText = ref('')
const statusFilter = ref<'全部' | '已发布' | '已下架'>('全部')
const sceneFilter = ref<ContentSceneFilter>('ALL')
const sortBy = ref<'default' | 'views' | 'downloads' | 'time'>('default')

const filteredAdminResources = computed(() => {
  let list = store.managedResources
  const text = searchText.value.trim().toLowerCase()
  if (text) {
    list = list.filter(r =>
      r.title.toLowerCase().includes(text) ||
      (r.desc || '').toLowerCase().includes(text) ||
      (r.tags || '').toLowerCase().includes(text) ||
      (r.author || '').toLowerCase().includes(text)
    )
  }
  if (statusFilter.value !== '全部') {
    list = list.filter(r => r.status === statusFilter.value)
  }
  if (sceneFilter.value !== 'ALL') {
    list = list.filter(r => r.scene === sceneFilter.value)
  }
  // client-side sort (views/downloads/time) applied to the (search+status+cat) filtered list
  const sorted = [...list]
  if (sortBy.value === 'views') {
    sorted.sort((a, b) => (b.views || 0) - (a.views || 0) || b.id - a.id)
  } else if (sortBy.value === 'downloads') {
    sorted.sort((a, b) => (b.downloads || 0) - (a.downloads || 0) || b.id - a.id)
  } else if (sortBy.value === 'time') {
    sorted.sort((a, b) => b.id - a.id)
  } else {
    sorted.sort((a, b) => b.id - a.id) // default newest by id
  }
  return sorted
})

onMounted(() => {
  store.loadResources().catch(() => undefined)
})

async function takeDownResource(id: number, title: string) {
  try {
    const { value } = await ElMessageBox.prompt(
      `下架「${title}」后，其他用户将无法访问。请填写下架原因。`,
      '下架资源',
      {
      type: 'warning',
      confirmButtonText: '下架',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '例如：附件失效、内容违规或版权问题',
      inputValidator: input => Boolean(input.trim()) || '必须填写下架原因'
      }
    )
    await store.takeDownResource(id, value)
    ElMessage.success('已下架')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '下架失败')
    }
  }
}

async function restoreOnline(id: number, title: string) {
  try {
    await ElMessageBox.confirm(`确认重新发布「${title}」？`, '恢复发布', { type: 'info' })
    await store.approveResource(id)
    ElMessage.success('资源已恢复发布')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '恢复失败')
    }
  }
}
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>内容管理</h1>
        <p class="sub">统一管理博客、图片和资料；频道不会限制正文或附件形式。</p>
      </div>
    </div>
    <div style="display:flex;gap:12px;align-items:center;margin-bottom:12px;flex-wrap:wrap;">
      <el-input v-model="searchText" placeholder="搜索标题/描述/标签/作者..." clearable style="width:280px;" />
      <el-select v-model="statusFilter" placeholder="状态" style="width:140px">
        <el-option label="全部" value="全部" />
        <el-option label="已发布" value="已发布" />
        <el-option label="已下架" value="已下架" />
      </el-select>
      <el-select v-model="sceneFilter" placeholder="频道" style="width:160px">
        <el-option label="全部频道" value="ALL" />
        <el-option v-for="scene in CONTENT_SCENES" :key="scene.value" :label="scene.label" :value="scene.value" />
      </el-select>
      <el-select v-model="sortBy" placeholder="排序" style="width:140px">
        <el-option label="默认（最新）" value="default" />
        <el-option label="浏览量 ↓" value="views" />
        <el-option label="下载量 ↓" value="downloads" />
        <el-option label="时间 ↓" value="time" />
      </el-select>
      <span class="sub">共 {{ filteredAdminResources.length }} 条</span>
    </div>
    <el-table :data="filteredAdminResources" class="panel">
      <el-table-column label="内容" min-width="280"><template #default="{ row }"><b>{{ row.title }}</b></template></el-table-column>
      <el-table-column label="频道" width="100"><template #default="{ row }">{{ contentSceneLabel(row.scene) }}</template></el-table-column>
      <el-table-column prop="author" label="发布者" width="110" />
      <el-table-column label="状态" width="120"><template #default="{ row }"><StatusTag :status="row.status" /></template></el-table-column>
      <el-table-column prop="downloads" label="下载" width="80" />
      <el-table-column label="浏览" width="80"><template #default="{ row }">{{ row.views || 0 }}</template></el-table-column>
      <el-table-column label="下架原因" min-width="180">
        <template #default="{ row }"><span class="sub">{{ row.offlineReason || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="250">
        <template #default="{ row }">
          <el-button size="small" @click="$router.push(`/detail/${row.id}`)">预览</el-button>
          <el-button size="small" type="primary" plain @click="$router.push(`/resource/${row.id}/edit`)">编辑</el-button>
          <el-button v-if="row.status === '已发布'" size="small" type="danger" plain @click="takeDownResource(row.id, row.title)">下架</el-button>
          <el-button v-else size="small" type="success" plain @click="restoreOnline(row.id, row.title)">恢复发布</el-button>
        </template>
      </el-table-column>
    </el-table>
  </AdminLayout>
</template>
