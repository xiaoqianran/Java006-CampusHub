<template>
  <div class="max-w-6xl mx-auto px-6 py-10">
    <div class="flex items-center gap-4 mb-8">
      <div class="w-16 h-16 rounded-3xl bg-[#0f766e]/10 flex items-center justify-center text-4xl">
        {{ userInitial }}
      </div>
      <div>
        <div class="text-3xl font-semibold">{{ user?.nickname || user?.username }}</div>
        <div class="text-sm text-[#5c4630]">{{ user?.username }} · {{ user?.role }}</div>
      </div>
      <div class="ml-auto text-right text-sm">
        <div>已发布 <span class="font-semibold text-[#0f766e]">{{ myUploads.length }}</span></div>
        <div>已收藏 <span class="font-semibold text-[#0f766e]">{{ favStore.count }}</span></div>
      </div>
    </div>

    <!-- Tabs -->
    <el-tabs v-model="activeTab" class="profile-tabs">
      <!-- 资料设置 -->
      <el-tab-pane label="资料设置" name="profile">
        <div class="shiqian-card p-8 max-w-xl">
          <el-form :model="profileForm" label-position="top">
            <el-form-item label="昵称">
              <el-input v-model="profileForm.nickname" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profileForm.email" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="profileForm.phone" />
            </el-form-item>
            <el-form-item label="头像 URL（可选）">
              <el-input v-model="profileForm.avatar" placeholder="https://..." />
            </el-form-item>
          </el-form>

          <el-button type="primary" @click="updateProfile" :loading="updating" class="mt-4">保存修改</el-button>
          <div class="text-xs text-[#8a7155] mt-2">修改后会实时更新到当前会话</div>
        </div>
      </el-tab-pane>

      <!-- 我的发布 -->
      <el-tab-pane :label="`我的发布 (${myUploads.length})`" name="uploads">
        <div v-if="myUploads.length === 0" class="empty-state">
          <div>你还没有发布过资源</div>
          <router-link to="/publish" class="text-[#0f766e] hover:underline mt-2 block">去发布第一份 →</router-link>
        </div>

        <div v-else class="space-y-3">
          <div v-for="res in myUploads" :key="res.id" class="shiqian-card p-5 flex items-center justify-between">
            <div>
              <router-link :to="`/resources/${res.id}`" class="font-medium hover:text-[#0f766e]">{{ res.title }}</router-link>
              <div class="text-xs text-[#8a7155] mt-0.5">{{ categoryStore.getCategoryName(res.categoryId) }} · v{{ res.version }} · {{ res.downloadCount }} 下载</div>
            </div>
            <div class="flex items-center gap-2">
              <span class="status-badge" :class="`status-${res.status}`">{{ getStatusText(res.status) }}</span>
              <el-button size="small" @click="editResource(res)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteResource(res.id)">删除</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 我的收藏 -->
      <el-tab-pane :label="`我的收藏 (${favStore.count})`" name="favorites">
        <div v-if="favStore.favoriteList.length === 0" class="empty-state">
          <div>还没有收藏任何资源</div>
          <router-link to="/resources" class="text-[#0f766e] hover:underline mt-2 block">去资源广场逛逛 →</router-link>
        </div>

        <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          <ResourceCard 
            v-for="res in favStore.favoriteList" 
            :key="res.id" 
            :resource="res" 
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useFavoritesStore } from '../stores/favorites'
import { useCategoryStore } from '../stores/category'
import { resourceApi } from '../api/resource'
import { userApi } from '../api/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import ResourceCard from '../components/ResourceCard.vue'
import type { ResourceItem } from '../types/resource'

const auth = useAuthStore()
const favStore = useFavoritesStore()
const categoryStore = useCategoryStore()

const activeTab = ref('profile')
const updating = ref(false)
const myUploads = ref<ResourceItem[]>([])

const user = computed(() => auth.user)
const userInitial = computed(() => (user.value?.nickname || user.value?.username || 'U')[0].toUpperCase())

const profileForm = reactive({
  nickname: '',
  email: '',
  phone: '',
  avatar: ''
})

function getStatusText(status: number) {
  if (status === 0) return '待审核'
  if (status === 2) return '已拒绝'
  return '已公开'
}

async function loadMyUploads() {
  try {
    // 拉取较多数据做客户端过滤（演示环境数据量小）
    const res = await resourceApi.pageResources({ page: 1, size: 100 })
    const uid = auth.currentUserId
    myUploads.value = (res.records || []).filter(r => r.userId === uid)
  } catch {}
}

async function loadFavorites() {
  await favStore.hydrateMyFavorites()
}

async function updateProfile() {
  updating.value = true
  try {
    await userApi.updateCurrentUser(profileForm)
    // 立即更新本地 store
    if (auth.user) {
      auth.user.nickname = profileForm.nickname || auth.user.nickname
    }
    ElMessage.success('资料已更新')
  } catch (e: any) {
    ElMessage.error(e.message || '更新失败')
  } finally {
    updating.value = false
  }
}

function editResource(res: ResourceItem) {
  // 简单实现：跳详情页（用户可手动更新）
  window.location.href = `/resources/${res.id}`
}

async function deleteResource(id: number) {
  try {
    await ElMessageBox.confirm('确定要删除这个资源吗？此操作不可恢复', '确认删除')
    await resourceApi.deleteResource(id)
    myUploads.value = myUploads.value.filter(r => r.id !== id)
    ElMessage.success('已删除')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

onMounted(async () => {
  if (!auth.isAuthenticated) return

  // 预填表单
  Object.assign(profileForm, {
    nickname: user.value?.nickname || '',
    email: '',
    phone: '',
    avatar: ''
  })

  await Promise.all([
    categoryStore.loadTree(),
    loadMyUploads(),
    loadFavorites()
  ])
})
</script>

<style>
.profile-tabs .el-tabs__header {
  margin-bottom: 24px;
}
</style>