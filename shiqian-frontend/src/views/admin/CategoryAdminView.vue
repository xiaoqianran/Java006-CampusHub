<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Download,
  Edit,
  Folder,
  MoreFilled,
  View
} from '@element-plus/icons-vue'
import AdminLayout from '@/components/AdminLayout.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore, type CategoryApiItem, type ResourceItem } from '@/stores/app'
import { buildApiUrl } from '@/api/client'

const store = useAppStore()
const router = useRouter()

const selectedCategory = ref<CategoryApiItem | null>(null)
const detailLoading = ref(false)

onMounted(() => {
  Promise.all([
    store.loadCategories(),
    store.loadResources()
  ]).catch(() => undefined)
})

const selectedResources = computed(() => {
  if (!selectedCategory.value) return []
  return store.resources.filter(item =>
    item.categoryId === selectedCategory.value?.id || item.cat === selectedCategory.value?.name
  )
})

const categoryCountMap = computed(() => {
  const map = new Map<number, number>()
  store.resources.forEach(item => {
    if (!item.categoryId) return
    map.set(item.categoryId, (map.get(item.categoryId) || 0) + 1)
  })
  return map
})

function categoryCount(category: CategoryApiItem) {
  return categoryCountMap.value.get(category.id)
    || store.resources.filter(item => item.cat === category.name).length
}

async function addCategory() {
  const name = await ElMessageBox.prompt('请输入分类名称', '新增分类')
    .then(({ value }) => value.trim())
    .catch(() => '')
  if (!name) return

  try {
    await store.createCategory(name)
    ElMessage.success('分类已新增')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '新增失败')
  }
}

async function editCategory(id: number, oldName: string) {
  const name = await ElMessageBox.prompt('请输入新的分类名称', '重命名分类', {
    inputValue: oldName,
    confirmButtonText: '保存',
    cancelButtonText: '取消'
  })
    .then(({ value }) => value.trim())
    .catch(() => '')

  if (!name || name === oldName) return

  try {
    await store.updateCategory(id, name)
    const latest = store.flatCategories.find(item => item.id === id)
    if (selectedCategory.value?.id === id && latest) {
      selectedCategory.value = latest
    }
    ElMessage.success('分类名称已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新失败')
  }
}

async function openCategory(category: CategoryApiItem) {
  detailLoading.value = true

  try {
    await store.loadResources({ categoryId: category.id })
    selectedCategory.value = category
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资源加载失败')
  } finally {
    detailLoading.value = false
  }
}

function backToCategories() {
  selectedCategory.value = null
}

function goDetail(resource: ResourceItem) {
  router.push(`/detail/${resource.id}`)
}

function openFile(resource: ResourceItem) {
  if (!resource.fileUrl) {
    ElMessage.warning('该资源没有文件地址')
    return
  }
  window.open(buildApiUrl(resource.fileUrl), '_blank')
}

function handleCategoryCommand(command: string, category: CategoryApiItem) {
  if (command === 'rename') {
    editCategory(category.id, category.name)
  }
}
</script>

<template>
  <AdminLayout>
    <template v-if="!selectedCategory">
      <div class="page-title">
        <div>
          <h1>分类管理</h1>
          <p class="sub">分类与用户端分类浏览共用同一套数据。点击文件夹可查看该分类下的资源。</p>
        </div>
        <el-button type="primary" @click="addCategory">新增分类</el-button>
      </div>

      <div class="admin-category-grid">
        <el-card
          v-for="category in store.flatCategories"
          :key="category.id"
          class="admin-category-folder"
          shadow="never"
          @click="openCategory(category)"
        >
          <div class="folder-action" @click.stop>
            <el-dropdown
              trigger="click"
              @command="(cmd: string) => handleCategoryCommand(cmd, category)"
            >
              <el-button
                class="folder-more"
                :icon="MoreFilled"
                circle
                aria-label="分类更多操作"
              />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename" :icon="Edit">
                    重命名分类
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <div class="folder-icon">
            <el-icon><Folder /></el-icon>
          </div>

          <div class="folder-name" :title="category.name">
            {{ category.name }}
          </div>

          <div class="folder-meta">
            {{ categoryCount(category) }} 个资源
          </div>

          <div class="folder-hint">
            点击查看内容
          </div>
        </el-card>
      </div>
    </template>

    <template v-else>
      <div class="category-detail-head">
        <el-button link :icon="ArrowLeft" class="category-back" @click="backToCategories">
          返回分类
        </el-button>

        <div class="category-breadcrumb">
          分类管理 / {{ selectedCategory.name }}
        </div>

        <div class="category-detail-title">
          <div>
            <h1>{{ selectedCategory.name }}</h1>
            <p class="sub">共 {{ selectedResources.length }} 个资源。这里预览该分类下的全部资源。</p>
          </div>

          <el-button :icon="Edit" @click="editCategory(selectedCategory.id, selectedCategory.name)">
            重命名分类
          </el-button>
        </div>
      </div>

      <el-card class="category-detail-panel no-white-flash" shadow="never" v-loading="detailLoading">
        <el-table
          v-if="selectedResources.length"
          :data="selectedResources"
          row-key="id"
          class="full"
        >
          <el-table-column label="资源名称" min-width="260">
            <template #default="{ row }">
              <b>{{ row.title }}</b>
              <p class="sub category-resource-desc">{{ row.desc || '暂无简介' }}</p>
            </template>
          </el-table-column>

          <el-table-column prop="type" label="类型" width="110" />

          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <StatusTag :status="row.status" />
            </template>
          </el-table-column>

          <el-table-column label="下载量" width="100">
            <template #default="{ row }">
              <span>
                <el-icon><Download /></el-icon>
                {{ row.downloads }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button size="small" :icon="View" @click="goDetail(row)">
                详情
              </el-button>
              <el-button size="small" :disabled="!row.fileUrl" @click="openFile(row)">
                文件
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty
          v-else
          description="该分类下暂无资源"
        />
      </el-card>
    </template>
  </AdminLayout>
</template>
