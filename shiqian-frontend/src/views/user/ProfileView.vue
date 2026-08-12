<script setup lang="ts">
import { reactive, ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UserFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const submitting = ref(false)
const passwordSubmitting = ref(false)

const form = reactive({
  nickname: '',
  email: '',
  phone: '',
  avatar: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

function resetForm() {
  const u = auth.currentUser
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

function resetPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

onMounted(() => {
  resetForm()
})

// 同步当前用户变化（例如外部更新后）
watch(() => auth.currentUser, resetForm, { deep: true })

async function saveProfile() {
  submitting.value = true
  try {
    const payload = {
      nickname: form.nickname || undefined,
      email: form.email || undefined,
      phone: form.phone || undefined,
      avatar: form.avatar || undefined
    }
    await auth.updateProfile(payload)
    ElMessage.success('个人资料已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新失败')
  } finally {
    submitting.value = false
  }
}

async function refreshProfile() {
  try {
    await auth.loadCurrentUser()
    ElMessage.success('资料已刷新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '刷新失败')
  }
}

async function savePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写原密码和新密码')
    return
  }
  if (passwordForm.newPassword.length < 6 || passwordForm.newPassword.length > 32) {
    ElMessage.warning('新密码长度需在 6-32 位之间')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  if (passwordForm.oldPassword === passwordForm.newPassword) {
    ElMessage.warning('新密码不能与原密码相同')
    return
  }

  passwordSubmitting.value = true
  try {
    await auth.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    resetPasswordForm()
    ElMessage.success('密码已修改，请使用新密码重新登录')
    await router.push('/login')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '修改密码失败')
  } finally {
    passwordSubmitting.value = false
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
              <el-input :model-value="auth.currentUser?.username || ''" disabled placeholder="用户名不可修改" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="角色">
              <el-input :model-value="auth.currentUser?.role === 'ADMIN' ? '管理员' : '学生'" disabled />
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

    <el-card class="form-card password-card" shadow="never">
      <template #header>
        <div>
          <span>修改密码</span>
          <p class="sub" style="margin: 4px 0 0; font-size: 12px; font-weight: normal;">
            修改成功后全部登录会话将失效，需使用新密码重新登录。
          </p>
        </div>
      </template>
      <el-form :model="passwordForm" label-position="top" style="max-width: 420px">
        <el-form-item label="原密码">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            show-password
            placeholder="请输入当前密码"
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            show-password
            placeholder="6-32 位新密码"
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            show-password
            placeholder="再次输入新密码"
            autocomplete="new-password"
          />
        </el-form-item>
        <div class="form-actions">
          <el-button type="primary" :loading="passwordSubmitting" @click="savePassword">
            确认修改密码
          </el-button>
          <el-button @click="resetPasswordForm" :disabled="passwordSubmitting">清空</el-button>
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
.password-card {
  margin-top: 16px;
}
</style>
