<script setup lang="ts">
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const store = useAppStore()
const form = reactive({
  title: '',
  cat: '计算机科学',
  type: '',
  desc: ''
})

function submit() {
  if (!form.title || !form.type || !form.desc) {
    ElMessage.warning('请补充标题、类型和简介')
    return
  }
  store.submitResource(form)
  ElMessage.success('已提交审核，可在我的发布中查看状态')
  router.push('/mine')
}
</script>

<template>
  <section>
    <div class="page-title">
      <div>
        <h1>发布资源</h1>
        <p class="sub">提交后进入“待审核”，后台审核通过后才进入资源广场。</p>
      </div>
    </div>
    <el-alert v-if="!store.logged" title="当前为未登录状态。为了让原型可演示，仍可填写；真实系统应先跳转登录。" type="warning" show-icon :closable="false" style="margin-bottom: 16px" />
    <el-card class="form-card" shadow="never">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item label="资源标题">
              <el-input v-model="form.title" placeholder="例如：Java 课程设计项目模板" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="资源分类">
              <el-select v-model="form.cat" class="full">
                <el-option v-for="category in store.categories" :key="category" :label="category" :value="category" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="资源类型">
              <el-input v-model="form.type" placeholder="笔记 / 实验报告 / 真题 / 项目模板" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="附件">
              <el-upload drag action="#" :auto-upload="false" class="full">
                <div>点击或拖拽文件到此处</div>
              </el-upload>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="资源简介">
              <el-input v-model="form.desc" type="textarea" :rows="5" placeholder="说明适用课程、内容范围、使用方法" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-button type="primary" @click="submit">提交审核</el-button>
        <el-button>保存草稿</el-button>
      </el-form>
    </el-card>
  </section>
</template>
