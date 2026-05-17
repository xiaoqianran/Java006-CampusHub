<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const store = useAppStore()

function login() {
  store.login()
  ElMessage.success('登录成功')
  router.push('/home')
}
</script>

<template>
  <section class="auth">
    <div class="auth-visual"><h1>欢迎回到时迁</h1><p>登录后学生端和后台端拥有统一入口，不再出现页面割裂。</p></div>
    <div class="auth-form">
      <h2>登录</h2>
      <el-form label-position="top">
        <el-form-item label="邮箱 / 用户名"><el-input placeholder="请输入账号" /></el-form-item>
        <el-form-item label="密码"><el-input type="password" placeholder="请输入密码" show-password /></el-form-item>
        <el-button type="primary" class="full" @click="login">登录</el-button>
      </el-form>
      <p class="sub">没有账号？<router-link to="/register">去注册</router-link></p>
    </div>
  </section>
</template>
