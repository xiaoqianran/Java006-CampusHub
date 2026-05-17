<template>
  <div class="min-h-[70vh] flex items-center justify-center px-6 pt-10">
    <div class="w-full max-w-md">
      <div class="text-center mb-8">
        <div class="inline-block w-12 h-12 rounded-2xl bg-[#0f766e] text-white text-3xl leading-[48px] mb-4">时</div>
        <h1 class="text-3xl font-semibold tracking-tight">加入时迁</h1>
        <p class="text-[#5c4630] mt-1">成为校园知识的贡献者</p>
      </div>

      <div class="shiqian-card p-8">
        <el-form :model="form" @submit.prevent="handleRegister" label-position="top">
          <el-form-item label="用户名 (4-20位字母数字_)">
            <el-input v-model="form.username" size="large" />
          </el-form-item>
          <el-form-item label="密码 (6-32位)">
            <el-input v-model="form.password" type="password" size="large" show-password />
          </el-form-item>
          <el-form-item label="昵称 (可选)">
            <el-input v-model="form.nickname" size="large" />
          </el-form-item>
          <el-form-item label="邮箱 (可选)">
            <el-input v-model="form.email" size="large" />
          </el-form-item>
          <el-form-item label="手机号 (可选)">
            <el-input v-model="form.phone" size="large" />
          </el-form-item>

          <el-button type="primary" native-type="submit" class="w-full !h-12 !text-base mt-2" :loading="loading">
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
const form = reactive({ username: '', password: '', nickname: '', email: '', phone: '' })
const loading = ref(false)

async function handleRegister() {
  if (!form.username || !form.password) {
    ElMessage.warning('用户名和密码必填')
    return
  }
  loading.value = true
  try {
    await userApi.register(form)
    ElMessage.success('注册成功！请登录')
    router.push('/login')
  } catch (e: any) {
    ElMessage.error(e.message || '注册失败（用户名/邮箱/手机号可能已存在）')
  } finally {
    loading.value = false
  }
}
</script>