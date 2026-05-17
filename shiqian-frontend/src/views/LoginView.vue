<template>
  <div class="min-h-[70vh] flex items-center justify-center px-6 pt-10">
    <div class="w-full max-w-md">
      <div class="text-center mb-8">
        <div class="inline-block w-12 h-12 rounded-2xl bg-[#0f766e] text-white text-3xl leading-[48px] mb-4">时</div>
        <h1 class="text-3xl font-semibold tracking-tight">欢迎回来</h1>
        <p class="text-[#5c4630] mt-1">登录后即可发布、收藏、下载资源</p>
      </div>

      <div class="shiqian-card p-8">
        <el-form :model="form" @submit.prevent="handleLogin" label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="学号或用户名" size="large" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" placeholder="••••••" size="large" show-password />
          </el-form-item>

          <el-button type="primary" native-type="submit" class="w-full !h-12 !text-base mt-2" :loading="loading">
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

const form = reactive<LoginRequest>({ username: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await userApi.login(form)
    // res 结构 { accessToken, refreshToken, userId, username, nickname, role }
    auth.setSession(
      { accessToken: res.accessToken, refreshToken: res.refreshToken },
      { userId: res.userId, username: res.username, nickname: res.nickname, role: res.role }
    )
    ElMessage.success(`欢迎回来，${res.nickname || res.username}！`)
    const redirect = (route.query.redirect as string) || '/resources'
    router.replace(redirect)
  } catch (e: any) {
    ElMessage.error(e.message || '登录失败，请检查用户名密码')
  } finally {
    loading.value = false
  }
}
</script>