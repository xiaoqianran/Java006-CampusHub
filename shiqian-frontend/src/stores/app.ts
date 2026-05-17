import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export type Role = 'student' | 'admin'
export type ResourceStatus = '已发布' | '待审核' | '已驳回'

export interface ResourceItem {
  id: number
  title: string
  cat: string
  type: string
  author: string
  views: number
  downloads: number
  favs: number
  status: ResourceStatus
  desc: string
}

export interface UserItem {
  id: number
  name: string
  role: string
  email: string
  status: '正常' | '禁用'
}

export const useAppStore = defineStore('app', () => {
  const role = ref<Role>('student')
  const logged = ref(false)
  const activeCategory = ref<string>('全部分类')
  const keyword = ref('')

  const categories = ref<string[]>([
    '计算机科学', '高等数学', '大学英语', '考研资料', '课程笔记', '实验报告', '竞赛资料', '校园生活'
  ])

  const resources = ref<ResourceItem[]>([
    { id: 1, title: '计算机网络实验三：路由协议配置', cat: '计算机科学', type: '实验报告', author: '林同学', views: 2380, downloads: 426, favs: 95, status: '已发布', desc: '包含 RIP、OSPF 路由配置步骤、拓扑图与常见错误排查。' },
    { id: 2, title: '数据结构期末复习提纲与真题解析', cat: '计算机科学', type: '复习资料', author: '王同学', views: 1560, downloads: 338, favs: 72, status: '待审核', desc: '覆盖线性表、树、图、排序、查找等核心知识点。' },
    { id: 3, title: '高等数学上册重点公式速查表', cat: '高等数学', type: '公式整理', author: '陈同学', views: 3420, downloads: 880, favs: 151, status: '已发布', desc: '极限、导数、积分、级数常见公式与题型归纳。' },
    { id: 4, title: '大学英语四级高频词汇与作文模板', cat: '大学英语', type: '考试资料', author: '李同学', views: 980, downloads: 220, favs: 34, status: '已驳回', desc: '词汇分组、作文句型、听力训练方法。' },
    { id: 5, title: 'Java Spring Boot 项目脚手架说明', cat: '计算机科学', type: '项目模板', author: '赵同学', views: 1890, downloads: 502, favs: 88, status: '已发布', desc: '适合课程设计使用的后端项目结构、常用依赖与接口示例。' },
    { id: 6, title: '考研数学一真题分类精讲', cat: '考研资料', type: '真题解析', author: '研友社', views: 2240, downloads: 420, favs: 130, status: '已发布', desc: '按知识点拆分近年真题，并配有解题思路。' }
  ])

  const users = ref<UserItem[]>([
    { id: 1, name: '李老师', role: '管理员', email: 'li@example.com', status: '正常' },
    { id: 2, name: '林同学', role: '学生', email: 'lin@example.com', status: '正常' },
    { id: 3, name: '王同学', role: '学生', email: 'wang@example.com', status: '禁用' },
    { id: 4, name: '研友社', role: '社团账号', email: 'club@example.com', status: '正常' }
  ])

  const favoriteIds = ref<number[]>([1, 3])
  const publishedIds = ref<number[]>([2, 4])

  const publishedResources = computed(() => resources.value.filter(item => item.status === '已发布'))
  const pendingResources = computed(() => resources.value.filter(item => item.status === '待审核'))
  const favoriteResources = computed(() => resources.value.filter(item => favoriteIds.value.includes(item.id)))
  const myResources = computed(() => resources.value.filter(item => publishedIds.value.includes(item.id)))

  const filteredResources = computed(() => {
    const text = keyword.value.trim()
    return publishedResources.value.filter(item => {
      const matchCategory = activeCategory.value === '全部分类' || item.cat === activeCategory.value
      const matchText = !text || `${item.title}${item.cat}${item.type}${item.desc}`.includes(text)
      return matchCategory && matchText
    })
  })

  function setRole(nextRole: Role) {
    role.value = nextRole
  }

  function login() {
    logged.value = true
  }

  function logout() {
    logged.value = false
    role.value = 'student'
  }

  function setCategory(category: string) {
    activeCategory.value = category
  }

  function resetFilters() {
    activeCategory.value = '全部分类'
    keyword.value = ''
  }

  function getResource(id: number) {
    return resources.value.find(item => item.id === id)
  }

  function isFavorite(id: number) {
    return favoriteIds.value.includes(id)
  }

  function toggleFavorite(id: number) {
    favoriteIds.value = isFavorite(id)
      ? favoriteIds.value.filter(item => item !== id)
      : [...favoriteIds.value, id]
  }

  function removeMyResource(id: number) {
    publishedIds.value = publishedIds.value.filter(item => item !== id)
  }

  function approveResource(id: number) {
    const item = getResource(id)
    if (item) item.status = '已发布'
  }

  function rejectResource(id: number) {
    const item = getResource(id)
    if (item) item.status = '已驳回'
  }

  function submitResource(payload: Pick<ResourceItem, 'title' | 'cat' | 'type' | 'desc'>) {
    const nextId = Math.max(...resources.value.map(item => item.id)) + 1
    resources.value.unshift({
      id: nextId,
      author: '当前用户',
      views: 0,
      downloads: 0,
      favs: 0,
      status: '待审核',
      ...payload
    })
    publishedIds.value.unshift(nextId)
    return nextId
  }

  return {
    role,
    logged,
    activeCategory,
    keyword,
    categories,
    resources,
    users,
    favoriteIds,
    publishedIds,
    publishedResources,
    pendingResources,
    favoriteResources,
    myResources,
    filteredResources,
    setRole,
    login,
    logout,
    setCategory,
    resetFilters,
    getResource,
    isFavorite,
    toggleFavorite,
    removeMyResource,
    approveResource,
    rejectResource,
    submitResource
  }
})
