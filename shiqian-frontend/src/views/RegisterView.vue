<template>
  <div class="min-h-[70vh] flex items-center justify-center px-6 pt-10">
    <div class="w-full max-w-md">
      <div class="text-center mb-8">
        <div class="inline-block w-12 h-12 rounded-2xl bg-[#0f766e] text-white text-3xl leading-[48px] mb-4">时</div>
        <h1 class="text-3xl font-semibold tracking-tight">加入时迁</h1>
        <p class="text-[#5c4630] mt-1">成为校园知识的贡献者</p>
      </div>

      <div class="shiqian-card p-8">
        <el-form :model="form" :rules="rules" ref="registerForm" @submit.prevent="handleRegister" label-position="top">
          <el-form-item label="用户名 (4-20位字母数字_)" prop="username">
            <el-input v-model="form.username" size="large" clearable autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码 (6-32位)" prop="password">
            <el-input v-model="form.password" type="password" size="large" show-password autocomplete="new-password" />
          </el-form-item>
          <el-form-item label="昵称 (可选)">
            <el-input v-model="form.nickname" size="large" clearable />
          </el-form-item>
          <el-form-item label="邮箱 (可选)" prop="email">
            <el-input v-model="form.email" size="large" clearable autocomplete="email" />
          </el-form-item>
          <el-form-item label="手机号 (可选)" prop="phone">
            <el-input v-model="form.phone" size="large" clearable autocomplete="tel" />
          </el-form-item>

          <el-button 
            type="primary" 
            native-type="submit" 
            class="w-full !h-12 !text-base mt-2" 
            :loading="loading"
            :disabled="!form.username || !form.password"
          >
            创建账号
          </el-button>
        </el-form>
        <div class="text-center text-sm mt-6 text-[#5c4630]">
          已有账号？ <router-link to="/login" class="text-[#0f766e] font-medium hover:underline">直接登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/user'

const router = useRouter()
const registerForm = ref()

const form = reactive({ username: '', password: '', nickname: '', email: '', phone: '' })
const loading = ref(false)

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度为4-20位', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为6-32位', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

async function handleRegister() {
  await registerForm.value?.validate().catch(() => {
    return Promise.reject()
  })

  loading.value = true
  try {
    await userApi.register(form)
    ElMessage.success('注册成功！请登录')
    // 跳转登录页并尝试预填用户名
    router.push({
      path: '/login',
      query: form.username ? { username: form.username } : {}
    })
  } catch (e: any) {
    const msg = e.message || '注册失败'
    if (msg.includes('用户名') || msg.includes('已存在')) {
      ElMessage.error('该用户名已被注册')
    } else if (msg.includes('邮箱')) {
      ElMessage.error('该邮箱已被注册')
    } else if (msg.includes('手机号')) {
      ElMessage.error('该手机号已被注册')
    } else {
      ElMessage.error(msg)
    }
  } finally {
    loading.value = false
  }
}
</script>