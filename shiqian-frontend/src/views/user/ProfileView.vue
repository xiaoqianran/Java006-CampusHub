<script setup lang="ts">
import { reactive, ref, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UserFilled } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const submitting = ref(false)

const form = reactive({
  nickname: '',
  email: '',
  phone: '',
  avatar: ''
})

function resetForm() {
  const u = store.currentUser
  if (u) {
    form.nickname = u.nickname || ''
    form.email = u.email || ''
    form.phone = u.phone || ''
    form.avatar = u.avatar || ''
  } else {
    form.nickname = ''
    form.email = ''
    form.phone = ''
    form.avatar = ''
  }
}

onMounted(() => {
  resetForm()
})

// 同步当前用户变化（例如外部更新后）
watch(() => store.currentUser, resetForm, { deep: true })

async function saveProfile() {
  submitting.value = true
  try {
    const payload = {
      nickname: form.nickname || undefined,
      email: form.email || undefined,
      phone: form.phone || undefined,
      avatar: form.avatar || undefined
    }
    await store.updateProfile(payload)
    ElMessage.success('个人资料已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新失败')
  } finally {
    submitting.value = false
  }
}

async function refreshProfile() {
  try {
    await store.loadCurrentUser()
    ElMessage.success('资料已刷新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '刷新失败')
  }
}
</script>

<template>
  <section>
    <div class="page-title">
      <div>
        <h1>个人资料</h1>
        <p class="sub">管理你的昵称、邮箱、手机与头像预览。保存后即时生效。</p>
      </div>
    </div>

    <el-card class="form-card" shadow="never">
      <el-form :model="form" label-position="top">
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12">
            <el-form-item label="用户名">
              <el-input :model-value="store.currentUser?.username || ''" disabled placeholder="用户名不可修改" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="角色">
              <el-input :model-value="store.currentUser?.role === 'ADMIN' ? '管理员' : '学生'" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :xs="24" :sm="12">
            <el-form-item label="昵称">
              <el-input v-model="form.nickname" placeholder="请输入昵称" clearable maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" placeholder="example@campus.edu" clearable type="email" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :xs="24" :sm="12">
            <el-form-item label="手机">
              <el-input v-model="form.phone" placeholder="请输入手机号" clearable maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <!-- 占位对齐 -->
            <el-form-item label=" ">
              <div style="height: 32px"></div>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 头像 -->
        <el-form-item label="头像 URL">
          <el-input
            v-model="form.avatar"
            placeholder="https://example.com/avatar.jpg （留空使用默认头像）"
            clearable
          />
          <div class="sub" style="margin-top: 6px; font-size: 12px;">支持任意可访问的图片地址，保存后用于平台展示。</div>
        </el-form-item>

        <el-form-item label="头像预览">
          <div class="avatar-preview">
            <el-avatar
              :size="88"
              :src="form.avatar || undefined"
              :icon="UserFilled"
              shape="square"
            />
            <div v-if="form.avatar" class="avatar-hint sub">当前输入预览（保存后生效）</div>
            <div v-else class="avatar-hint sub">未设置头像，将显示默认用户图标</div>
          </div>
        </el-form-item>

        <div class="form-actions">
          <el-button type="primary" :loading="submitting" @click="saveProfile">
            保存修改
          </el-button>
          <el-button @click="resetForm" :disabled="submitting">重置表单</el-button>
          <el-button @click="refreshProfile" :disabled="submitting">刷新最新资料</el-button>
        </div>
      </el-form>
    </el-card>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-top: 16px"
    >
      <template #title>提示</template>
      头像目前通过 URL 方式设置。昵称、邮箱、手机修改后会立即更新到当前会话与相关列表中。
    </el-alert>
  </section>
</template>

<style scoped>
.avatar-preview {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 8px 0;
}
.avatar-hint {
  font-size: 12px;
  max-width: 240px;
}
.form-actions {
  margin-top: 8px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
