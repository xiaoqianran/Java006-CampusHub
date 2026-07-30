<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Delete,
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

// Category create/edit dialog form (supports icon emoji/URL + sortOrder)
const categoryFormVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const categoryForm = ref<{ name: string; icon: string; sortOrder: number }>({
  name: '',
  icon: '',
  sortOrder: 0
})

onMounted(() => {
  Promise.all([
    store.loadCategories(),
    store.loadResources()
  ]).catch(() => undefined)
})

const selectedResources = computed(() => {
  if (!selectedCategory.value) return []
  return store.resources.filter(item =>
    item.categoryIds.includes(selectedCategory.value!.id)
    || item.categoryId === selectedCategory.value?.id
    || item.cat === selectedCategory.value?.name
  )
})

const categoryCountMap = computed(() => {
  const map = new Map<number, number>()
  store.resources.forEach(item => {
    const categoryIds = item.categoryIds.length
      ? item.categoryIds
      : item.categoryId ? [item.categoryId] : []
    new Set(categoryIds).forEach(categoryId => {
      map.set(categoryId, (map.get(categoryId) || 0) + 1)
    })
  })
  return map
})

function categoryCount(category: CategoryApiItem) {
  return categoryCountMap.value.get(category.id)
    || store.resources.filter(item => item.cat === category.name).length
}

// Open create dialog (computes reasonable default sortOrder)
function openAddCategory() {
  isEditing.value = false
  editingId.value = null
  const maxSort = store.flatCategories.reduce((m, c) => Math.max(m, c.sortOrder || 0), 0)
  categoryForm.value = {
    name: '',
    icon: '',
    sortOrder: maxSort + 10
  }
  categoryFormVisible.value = true
}

// Open edit dialog prefilled with current values (icon + sortOrder editable)
function openEditCategory(category: CategoryApiItem) {
  isEditing.value = true
  editingId.value = category.id
  categoryForm.value = {
    name: category.name || '',
    icon: category.icon || '',
    sortOrder: category.sortOrder ?? 0
  }
  categoryFormVisible.value = true
}

// Submit handler wires to (extended) store methods
async function submitCategoryForm() {
  const name = categoryForm.value.name.trim()
  if (!name) {
    ElMessage.warning('分类名称不能为空')
    return
  }
  const icon = categoryForm.value.icon.trim()
  const sortOrder = categoryForm.value.sortOrder

  try {
    if (isEditing.value && editingId.value != null) {
      await store.updateCategory(editingId.value, name, icon || undefined, sortOrder)
      const latest = store.flatCategories.find(item => item.id === editingId.value)
      if (selectedCategory.value?.id === editingId.value && latest) {
        selectedCategory.value = latest
      }
      await store.recordAdminLog('CATEGORY_UPDATE', editingId.value, name)
      ElMessage.success('分类已更新（含图标/排序）')
    } else {
      await store.createCategory(name, icon || undefined, sortOrder)
      const created = store.flatCategories.find(c => c.name === name)
      if (created) await store.recordAdminLog('CATEGORY_CREATE', created.id, name)
      ElMessage.success('分类已新增（支持图标与排序）')
    }
    categoryFormVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

// Legacy wrappers kept only for reference (no longer used); new UI uses open* + submit
// (removed prompt-based logic to support multi-field form)


async function deleteCategoryConfirm(id: number, name: string) {
  try {
    await ElMessageBox.confirm(`确认删除分类「${name}」？仅当无子分类时允许删除，资源与该分类的关联会被自动清理。`, '删除分类', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await store.deleteCategory(id)
    await store.recordAdminLog('CATEGORY_DELETE', id, name)
    if (selectedCategory.value?.id === id) {
      selectedCategory.value = null
    }
    ElMessage.success('分类已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败')
    }
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
  if (command === 'edit') {
    openEditCategory(category)
  } else if (command === 'delete') {
    deleteCategoryConfirm(category.id, category.name)
  }
}
</script>

<template>
  <AdminLayout>
    <template v-if="!selectedCategory">
      <div class="page-title">
        <div>
          <h1>分类管理</h1>
          <p class="sub">分类仅作为可选的历史与辅助元数据，不再是用户发布或浏览内容的必填入口。</p>
        </div>
        <el-button type="primary" @click="openAddCategory">新增分类</el-button>
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
                  <el-dropdown-item command="edit" :icon="Edit">
                    编辑分类（图标/排序）
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" :icon="Delete">
                    删除分类
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <div class="folder-icon">
            <!-- Support icon as emoji (e.g. 📚) or image URL; fallback to Folder -->
            <span v-if="category.icon && !/^https?:\/\//.test(category.icon)" class="emoji-icon">{{ category.icon }}</span>
            <img v-else-if="category.icon" :src="category.icon" class="custom-icon" :alt="category.name + ' icon'" />
            <el-icon v-else><Folder /></el-icon>
          </div>

          <div class="folder-name" :title="category.name">
            {{ category.name }}
          </div>

          <div class="folder-meta">
            <span class="sort-badge">#{{ category.sortOrder ?? 0 }}</span>
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

          <el-button :icon="Edit" @click="openEditCategory(selectedCategory)">
            编辑分类（图标/排序）
          </el-button>
          <el-button type="danger" plain @click="deleteCategoryConfirm(selectedCategory.id, selectedCategory.name)">
            删除分类
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

    <!-- Create/Edit Category Dialog: icon (emoji or URL) + sortOrder + name -->
    <el-dialog
      v-model="categoryFormVisible"
      :title="isEditing ? '编辑分类' : '新增分类'"
      width="440px"
      destroy-on-close
    >
      <el-form :model="categoryForm" label-width="90px" @submit.prevent="submitCategoryForm">
        <el-form-item label="名称" required>
          <el-input
            v-model="categoryForm.name"
            placeholder="分类名称，例如：算法笔记"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="图标">
          <el-input
            v-model="categoryForm.icon"
            placeholder="emoji 如 📚 或图片URL (https://...)"
            maxlength="255"
          />
          <div v-if="categoryForm.icon" class="icon-preview">
            预览：
            <span v-if="!/^https?:\/\//.test(categoryForm.icon)" class="emoji-preview">{{ categoryForm.icon }}</span>
            <img
              v-else
              :src="categoryForm.icon"
              class="custom-icon-preview"
              alt="icon preview"
            />
          </div>
          <div class="form-hint">支持emoji字符或外部图片URL，留空则使用默认文件夹图标</div>
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number
            v-model="categoryForm.sortOrder"
            :min="0"
            :max="99999"
            controls-position="right"
            style="width: 160px"
          />
          <span class="form-hint" style="margin-left: 8px;">数值越小排序越靠前（树与列表均按此排序）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="categoryFormVisible = false">取消</el-button>
          <el-button type="primary" @click="submitCategoryForm">
            {{ isEditing ? '保存修改' : '创建分类' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </AdminLayout>
</template>

<style scoped>
/* Icon & sort enhancements for admin category cards */
.folder-icon {
  font-size: 28px;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
}

.emoji-icon {
  font-size: 28px;
  line-height: 1;
}

.custom-icon {
  width: 28px;
  height: 28px;
  object-fit: contain;
  border-radius: 4px;
}

.sort-badge {
  display: inline-block;
  font-size: 11px;
  background: var(--el-fill-color-light, #f0f0f0);
  color: var(--el-text-color-secondary, #666);
  padding: 1px 5px;
  border-radius: 3px;
  margin-right: 6px;
  font-family: monospace;
}

.icon-preview,
.form-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  margin-top: 4px;
}

.emoji-preview {
  font-size: 20px;
  vertical-align: middle;
}

.custom-icon-preview {
  width: 20px;
  height: 20px;
  object-fit: contain;
  vertical-align: middle;
  border-radius: 3px;
  margin-left: 4px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* Subtle improvement to admin category grid for tree-ish feel (sorted by sortOrder from backend) */
.admin-category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
}

.admin-category-folder {
  position: relative;
  cursor: pointer;
  transition: transform 0.1s ease, box-shadow 0.1s ease;
}

.admin-category-folder:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.folder-name {
  font-weight: 600;
  font-size: 14px;
  margin: 4px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* Dark theme tweaks via global but scoped fallback */
[data-theme="dark"] .sort-badge {
  background: #2a2a2a;
  color: #aaa;
}
</style>
