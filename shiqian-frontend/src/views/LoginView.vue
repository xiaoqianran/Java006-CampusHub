<template>
  <div class="min-h-[70vh] flex items-center justify-center px-6 pt-10">
    <div class="w-full max-w-md">
      <div class="text-center mb-8">
        <div class="inline-block w-12 h-12 rounded-2xl bg-[#0f766e] text-white text-3xl leading-[48px] mb-4">时</div>
        <h1 class="text-3xl font-semibold tracking-tight">欢迎回来</h1>
        <p class="text-[#5c4630] mt-1">登录后即可发布、收藏、下载资源</p>
      </div>

      <div class="shiqian-card p-8">
        <el-form :model="form" :rules="rules" ref="loginForm" @submit.prevent="handleLogin" label-position="top">
          <el-form-item label="用户名" prop="username">
            <el-input 
              v-model="form.username" 
              placeholder="学号或用户名" 
              size="large" 
              clearable 
              autocomplete="username" 
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input 
              v-model="form.password" 
              type="password" 
              placeholder="••••••" 
              size="large" 
              show-password 
              autocomplete="current-password" 
            />
          </el-form-item>

          <el-button 
            type="primary" 
            native-type="submit" 
            class="w-full !h-12 !text-base mt-2" 
            :loading="loading"
            :disabled="!form.username || !form.password"
          >
            登录
          </el-button>
        </el-form>

        <div class="text-center text-sm mt-6 text-[#5c4630]">
          还没有账号？
          <router-link to="/register" class="text-[#0f766e] font-medium hover:underline">立即注册</router-link>
        </div>
      </div>
      <div class="text-[10px] text-center text-[#8a7155] mt-8">演示账号可使用任意已注册的学生信息</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/user'
import { useAuthStore } from '../stores/auth'
import type { LoginRequest } from '../types/user'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loginForm = ref()

const form = reactive<LoginRequest>({ username: '', password: '' })
const loading = ref(false)

// 从注册页跳转时预填用户名
const queryUsername = route.query.username as string
if (queryUsername) {
  form.username = queryUsername
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度为4-20位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为6-32位', trigger: 'blur' }
  ]
}

async function handleLogin() {
  // 先进行表单校验
  await loginForm.value?.validate().catch(() => {
    return Promise.reject()
  })

  loading.value = true
  try {
    const res = await userApi.login(form)
    auth.setSession(
      { accessToken: res.accessToken, refreshToken: res.refreshToken },
      { userId: res.userId, username: res.username, nickname: res.nickname, role: res.role }
    )
    ElMessage.success(`欢迎回来，${res.nickname || res.username}！`)
    const redirect = (route.query.redirect as string) || '/resources'
    router.replace(redirect)
  } catch (e: any) {
    // 更友好的错误提示
    const msg = e.message || '登录失败'
    if (msg.includes('用户名') || msg.includes('密码')) {
      ElMessage.error('用户名或密码错误')
    } else {
      ElMessage.error(msg)
    }
  } finally {
    loading.value = false
  }
}
</script>