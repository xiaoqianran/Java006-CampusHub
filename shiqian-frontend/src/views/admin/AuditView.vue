<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import AdminLayout from '@/components/AdminLayout.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore, type ResourceItem } from '@/stores/app'

const router = useRouter()
const store = useAppStore()

const detailVisible = ref(false)
const detailLoading = ref(false)
const openingId = ref<number | null>(null)
const current = ref<ResourceItem | null>(null)

onMounted(() => {
  store.loadHomeData().catch(() => undefined)
})

async function openDetail(row: ResourceItem) {
  current.value = row
  detailVisible.value = true
  detailLoading.value = true
  openingId.value = row.id

  try {
    await store.loadResourceDetail(row.id)
    current.value = store.getResource(row.id) || row
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资源详情加载失败')
  } finally {
    detailLoading.value = false
    openingId.value = null
  }
}

function openFile(row?: ResourceItem | null) {
  if (!row?.fileUrl) {
    ElMessage.warning('该资源没有可查看的文件地址')
    return
  }
  window.open(row.fileUrl, '_blank')
}

function goDetail(row?: ResourceItem | null) {
  if (!row) return
  router.push(`/detail/${row.id}`)
}

async function approve(id: number) {
  try {
    await ElMessageBox.confirm('确认通过该资源审核吗？', '审核确认', { type: 'success' })
    await store.approveResource(id)
    ElMessage.success('已通过')
    detailVisible.value = false
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '审核失败')
    }
  }
}

async function reject(id: number) {
  try {
    await ElMessageBox.confirm('确认驳回该资源吗？', '驳回确认', { type: 'warning' })
    await store.rejectResource(id)
    ElMessage.warning('已驳回')
    detailVisible.value = false
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error instanceof Error ? error.message : '审核失败')
    }
  }
}
</script>

<template>
  <AdminLayout>
    <div class="page-title">
      <div>
        <h1>资源审核</h1>
        <p class="sub">审核待发布资源，支持查看详情与原文件后再决定通过或驳回。</p>
      </div>
    </div>

    <el-table :data="store.pendingResources" class="panel audit-table" style="width: 100%">
      <el-table-column label="资源信息" min-width="320">
        <template #default="{ row }">
          <b>{{ row.title }}</b>
          <div class="sub audit-desc">{{ row.desc || '暂无简介' }}</div>
        </template>
      </el-table-column>

      <el-table-column prop="cat" label="分类" width="130" />
      <el-table-column prop="type" label="类型" width="140" />
      <el-table-column prop="author" label="发布者" width="130" />

      <el-table-column label="文件" width="120">
        <template #default="{ row }">
          <el-button v-if="row.fileUrl" size="small" text type="primary" @click="openFile(row)">
            查看文件
          </el-button>
          <span v-else class="sub">暂无文件</span>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <StatusTag :status="row.status" />
        </template>
      </el-table-column>

      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <div class="audit-actions">
            <!-- 第一层：审阅动作（最显眼） -->
            <el-button
              size="small"
              type="primary"
              class="audit-action-review"
              :loading="openingId === row.id"
              @click="openDetail(row)"
            >
              查看详情
            </el-button>

            <el-button
              size="small"
              class="audit-action-file"
              :disabled="!row.fileUrl"
              @click="openFile(row)"
            >
              打开文件
            </el-button>

            <span class="audit-action-divider"></span>

            <!-- 第二层：审核决策 -->
            <el-button
              size="small"
              class="audit-action-approve"
              @click="approve(row.id)"
            >
              通过
            </el-button>

            <el-button
              size="small"
              class="audit-action-reject"
              @click="reject(row.id)"
            >
              驳回
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!store.pendingResources.length" description="暂无待审核资源" />

    <el-dialog
      v-model="detailVisible"
      title="资源审核详情"
      width="720px"
      class="audit-detail-dialog"
      modal-class="audit-detail-modal"
      :destroy-on-close="false"
      :lock-scroll="false"
      :close-on-click-modal="false"
      @closed="current = null"
    >
      <div v-loading="detailLoading" class="audit-detail no-white-flash">
        <h2>{{ current?.title || '资源审核详情' }}</h2>

        <div class="audit-meta">
          <span>分类：{{ current?.cat || '-' }}</span>
          <span>类型：{{ current?.type || '-' }}</span>
          <span>发布者：{{ current?.author || '-' }}</span>
          <span>文件大小：{{ current?.fileSize || 0 }} 字节</span>
        </div>

        <div class="audit-section">
          <h3>资源简介</h3>
          <p>{{ current?.desc || '暂无简介' }}</p>
        </div>

        <div class="audit-section">
          <h3>原文件</h3>
          <p v-if="current?.fileUrl" class="audit-file-url">{{ current?.fileUrl }}</p>
          <p v-else class="sub">该资源没有可查看的文件地址。</p>
        </div>
      </div>

      <template #footer>
        <div class="audit-actions" style="width: 100%; justify-content: space-between;">
          <div>
            <el-button @click="detailVisible = false">关闭</el-button>
            <el-button :disabled="!current?.fileUrl" class="audit-action-file" @click="openFile(current)">打开原文件</el-button>
            <el-button v-if="current" class="audit-action-review" @click="goDetail(current)">跳转详情页</el-button>
          </div>

          <div>
            <el-button v-if="current" class="audit-action-reject" @click="reject(current.id)">驳回</el-button>
            <el-button v-if="current" class="audit-action-approve" @click="approve(current.id)">通过</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </AdminLayout>
</template>
