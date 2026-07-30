<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminLayout from '@/components/AdminLayout.vue'
import { useAppStore, type TagApiItem } from '@/stores/app'

const store = useAppStore()
const keyword = ref('')
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref<TagApiItem | null>(null)
const tagName = ref('')

const filteredTags = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return text
    ? store.tags.filter(tag => tag.name.toLowerCase().includes(text))
    : store.tags
})

onMounted(() => {
  void refresh()
})

async function refresh() {
  loading.value = true
  try {
    await store.loadTags()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '标签加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  tagName.value = ''
  dialogVisible.value = true
}

function openEdit(tag: TagApiItem) {
  editing.value = tag
  tagName.value = tag.name
  dialogVisible.value = true
}

async function saveTag() {
  const name = tagName.value.trim()
  if (!name) {
    ElMessage.warning('请输入标签名称')
    return
  }
  try {
    if (editing.value) {
      await store.updateTag(editing.value.id, name)
      ElMessage.success('标签已更新，关联资源索引将自动同步')
    } else {
      await store.createTag(name)
      ElMessage.success('标签已创建')
    }
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '标签保存失败')
  }
}

async function removeTag(tag: TagApiItem) {
  try {
    await ElMessageBox.confirm(
      `确认删除标签「${tag.name}」？资源本身会保留，但与该标签的关联会清理。`,
      '删除标签',
      { type: 'warning', confirmButtonText: '删除' }
    )
    await store.deleteTag(tag.id)
    ElMessage.success('标签及其资源关联已删除')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '标签删除失败')
  }
}
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>标签管理</h1>
        <p class="sub">标签是可选的自由维度；发布者也可以直接创建新标签。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增标签</el-button>
    </div>

    <el-card shadow="never">
      <div class="tag-toolbar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索标签"
          style="max-width: 320px"
        />
        <span class="sub">共 {{ filteredTags.length }} 个</span>
      </div>
      <el-table v-loading="loading" :data="filteredTags" stripe>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="name" label="标签名称" min-width="220">
          <template #default="{ row }">
            <el-tag effect="plain"># {{ row.name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">重命名</el-button>
            <el-button size="small" type="danger" plain @click="removeTag(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑标签' : '新增标签'"
      width="420px"
    >
      <el-input
        v-model="tagName"
        maxlength="50"
        show-word-limit
        placeholder="输入标签名称"
        @keyup.enter="saveTag"
      />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTag">保存</el-button>
      </template>
    </el-dialog>
  </AdminLayout>
</template>

<style scoped>
.tag-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
</style>
