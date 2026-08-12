<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminLayout from '@/components/AdminLayout.vue'
import { useAdminStore } from '@/stores/admin'
import type { ContentReviewRecordItem, SensitiveWordItem } from '@/stores/types'

const admin = useAdminStore()
const activeTab = ref('words')
const words = ref<SensitiveWordItem[]>([])
const records = ref<ContentReviewRecordItem[]>([])
const keyword = ref('')
const loadingWords = ref(false)
const loadingRecords = ref(false)
const recordPage = ref(1)
const recordTotal = ref(0)
const reviewType = ref('')
const decision = ref('')
const resourceId = ref<number>()
const dialogVisible = ref(false)
const editingId = ref<number>()
const form = reactive({ word: '', level: 2, status: 1 })

async function loadWords() {
  loadingWords.value = true
  try {
    words.value = await admin.loadSensitiveWords(keyword.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载敏感词失败')
  } finally {
    loadingWords.value = false
  }
}

async function loadRecords(page = 1) {
  loadingRecords.value = true
  try {
    const data = await admin.loadContentReviewRecords({
      page,
      size: 20,
      reviewType: reviewType.value,
      decision: decision.value,
      resourceId: resourceId.value
    })
    records.value = data.records || []
    recordTotal.value = data.total || 0
    recordPage.value = page
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载审核记录失败')
  } finally {
    loadingRecords.value = false
  }
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, { word: '', level: 2, status: 1 })
  dialogVisible.value = true
}

function openEdit(row: SensitiveWordItem) {
  editingId.value = row.id
  Object.assign(form, { word: row.word, level: row.level, status: row.status })
  dialogVisible.value = true
}

async function saveWord() {
  const word = form.word.trim()
  if (!word) {
    ElMessage.warning('请输入敏感词')
    return
  }
  try {
    const payload = { word, level: form.level, status: form.status }
    if (editingId.value) {
      await admin.updateSensitiveWord(editingId.value, payload)
    } else {
      await admin.createSensitiveWord(payload)
    }
    dialogVisible.value = false
    ElMessage.success('敏感词规则已生效')
    await loadWords()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  }
}

async function removeWord(row: SensitiveWordItem) {
  try {
    await ElMessageBox.confirm(`确认删除“${row.word}”吗？`, '删除敏感词', { type: 'warning' })
    await admin.deleteSensitiveWord(row.id)
    ElMessage.success('已删除并热更新')
    await loadWords()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败')
    }
  }
}

async function reloadRules() {
  try {
    await admin.reloadSensitiveWords()
    ElMessage.success('已从数据库重新加载规则')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重新加载失败')
  }
}

onMounted(() => {
  Promise.allSettled([loadWords(), loadRecords()])
})
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>内容安全</h1>
        <p class="sub">维护热更新敏感词，并追溯自动拦截与人工审核结果。</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="panel" style="padding: 0 20px 20px;">
      <el-tab-pane label="敏感词规则" name="words">
        <div class="toolbar" style="display:flex;gap:10px;flex-wrap:wrap;margin-bottom:14px;">
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索敏感词"
            style="width:240px"
            @keyup.enter="loadWords"
          />
          <el-button @click="loadWords">查询</el-button>
          <el-button @click="reloadRules">从数据库重载</el-button>
          <el-button type="primary" @click="openCreate">新增规则</el-button>
        </div>
        <el-table :data="words" v-loading="loadingWords" stripe>
          <el-table-column prop="word" label="敏感词" min-width="180" />
          <el-table-column label="级别" width="110">
            <template #default="{ row }">
              <el-tag :type="row.level === 3 ? 'danger' : row.level === 2 ? 'warning' : 'info'">
                L{{ row.level }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="190" />
          <el-table-column label="操作" width="170">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="removeWord(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="审核记录" name="records">
        <div class="toolbar" style="display:flex;gap:10px;flex-wrap:wrap;margin-bottom:14px;">
          <el-select v-model="reviewType" clearable placeholder="审核来源" style="width:140px" @change="loadRecords(1)">
            <el-option label="自动审核" value="AUTO" />
            <el-option label="人工审核" value="MANUAL" />
          </el-select>
          <el-select v-model="decision" clearable placeholder="审核结论" style="width:160px" @change="loadRecords(1)">
            <el-option label="自动拦截" value="BLOCKED" />
            <el-option label="通过" value="APPROVED" />
            <el-option label="退回修改" value="NEEDS_CHANGES" />
            <el-option label="拒绝" value="REJECTED" />
            <el-option label="下架" value="OFFLINE" />
          </el-select>
          <el-input-number
            v-model="resourceId"
            :min="1"
            :controls="false"
            placeholder="资源ID"
            style="width:140px"
            @change="loadRecords(1)"
          />
          <el-button @click="loadRecords(1)">刷新</el-button>
        </div>
        <el-table :data="records" v-loading="loadingRecords" stripe>
          <el-table-column prop="createTime" label="时间" width="190" />
          <el-table-column label="来源" width="100">
            <template #default="{ row }">{{ row.reviewType === 'AUTO' ? '自动' : '人工' }}</template>
          </el-table-column>
          <el-table-column prop="decision" label="结论" width="150" />
          <el-table-column prop="resourceId" label="资源ID" width="100" />
          <el-table-column prop="contentTitle" label="标题" min-width="180" />
          <el-table-column prop="matchedWords" label="命中规则" min-width="160" />
          <el-table-column prop="reason" label="原因" min-width="200" />
          <el-table-column prop="reviewerId" label="审核人ID" width="110" />
        </el-table>
        <div style="display:flex;justify-content:flex-end;margin-top:16px;">
          <el-pagination
            v-model:current-page="recordPage"
            :page-size="20"
            :total="recordTotal"
            layout="prev, pager, next, total"
            @current-change="loadRecords"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑敏感词' : '新增敏感词'" width="460px">
      <el-form label-width="80px">
        <el-form-item label="敏感词">
          <el-input v-model="form.word" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="级别">
          <el-radio-group v-model="form.level">
            <el-radio-button :value="1">L1</el-radio-button>
            <el-radio-button :value="2">L2</el-radio-button>
            <el-radio-button :value="3">L3</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveWord">保存并生效</el-button>
      </template>
    </el-dialog>
  </AdminLayout>
</template>
