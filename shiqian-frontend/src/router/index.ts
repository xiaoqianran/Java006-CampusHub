import { createRouter, createWebHashHistory, RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import HomeView from '@/views/user/HomeView.vue'

const PlazaView = () => import('@/views/user/PlazaView.vue')
const PublishView = () => import('@/views/user/PublishView.vue')
const FavoritesView = () => import('@/views/user/FavoritesView.vue')
const MineView = () => import('@/views/user/MineView.vue')
const ProfileView = () => import('@/views/user/ProfileView.vue')
const DetailView = () => import('@/views/user/DetailView.vue')
const LoginView = () => import('@/views/user/LoginView.vue')
const RegisterView = () => import('@/views/user/RegisterView.vue')
const ResourceEditView = () => import('@/views/user/ResourceEditView.vue')
const AdminHomeView = () => import('@/views/admin/AdminHomeView.vue')
const AuditView = () => import('@/views/admin/AuditView.vue')
const ResourceAdminView = () => import('@/views/admin/ResourceAdminView.vue')
const RecycleBinView = () => import('@/views/admin/RecycleBinView.vue')
const CategoryAdminView = () => import('@/views/admin/CategoryAdminView.vue')
const TagAdminView = () => import('@/views/admin/TagAdminView.vue')
const UserAdminView = () => import('@/views/admin/UserAdminView.vue')
const AdminLogView = () => import('@/views/admin/AdminLogView.vue')
const ContentModerationView = () => import('@/views/admin/ContentModerationView.vue')

const adminMeta = { requiresAuth: true, roles: ['admin'] }

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/home' },
  { path: '/home', component: HomeView, meta: { title: '首页' } },
  { path: '/explore', component: PlazaView, meta: { title: '发现内容', scene: 'ALL' } },
  { path: '/blog', component: PlazaView, meta: { title: '博客', scene: 'BLOG' } },
  { path: '/images', component: PlazaView, meta: { title: '图片', scene: 'GALLERY' } },
  { path: '/share', component: PlazaView, meta: { title: '资料', scene: 'SHARE' } },
  { path: '/resources', redirect: to => ({ path: '/share', query: to.query }) },
  { path: '/plaza', redirect: to => ({ path: '/explore', query: to.query }) },
  { path: '/categories', redirect: to => ({ path: '/explore', query: to.query }) },
  { path: '/publish', component: PublishView, meta: { title: '发布内容', requiresAuth: true } },
  { path: '/favorites', component: FavoritesView, meta: { title: '我的收藏', requiresAuth: true } },
  { path: '/mine', component: MineView, meta: { title: '我的发布', requiresAuth: true } },
  { path: '/profile', component: ProfileView, meta: { title: '个人资料', requiresAuth: true } },
  { path: '/detail/:id', component: DetailView, meta: { title: '内容详情' } },
  { path: '/resource/:id/edit', component: ResourceEditView, meta: { title: '编辑内容', requiresAuth: true } },
  { path: '/login', component: LoginView, meta: { title: '登录' } },
  { path: '/register', component: RegisterView, meta: { title: '注册' } },
  { path: '/admin', component: AdminHomeView, meta: { ...adminMeta, title: '后台首页' } },
  { path: '/admin/audit', component: AuditView, meta: { ...adminMeta, title: '内容审核' } },
  { path: '/admin/resources', component: ResourceAdminView, meta: { ...adminMeta, title: '内容管理' } },
  { path: '/admin/recycle-bin', component: RecycleBinView, meta: { ...adminMeta, title: '回收站' } },
  { path: '/admin/categories', component: CategoryAdminView, meta: { ...adminMeta, title: '分类管理' } },
  { path: '/admin/tags', component: TagAdminView, meta: { ...adminMeta, title: '标签管理' } },
  { path: '/admin/users', component: UserAdminView, meta: { ...adminMeta, title: '用户管理' } },
  { path: '/admin/content-moderation', component: ContentModerationView, meta: { ...adminMeta, title: '内容安全' } },
  { path: '/admin/logs', component: AdminLogView, meta: { ...adminMeta, title: '操作日志' } },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async to => {
  const auth = useAuthStore()

  // Access Token 不再持久化。每个新页面只在首次导航时使用 HttpOnly Cookie 恢复会话。
  if (!auth.initialized) {
    await auth.restoreSession()
  }

  const requiresAuth = Boolean(to.meta.requiresAuth)
  const roles = to.meta.roles as string[] | undefined
  const isAdminRoute = to.path === '/admin' || to.path.startsWith('/admin/')

  if ((requiresAuth || isAdminRoute) && !auth.logged) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if ((requiresAuth || isAdminRoute) && auth.logged && !auth.currentUser) {
    try {
      await auth.loadCurrentUser()
    } catch {
      await auth.logout()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  const backendRole = auth.currentUser?.role
  if (isAdminRoute && backendRole !== 'ADMIN') {
    return { path: '/home' }
  }

  if (roles?.includes('admin') && backendRole !== 'ADMIN') {
    return { path: '/home' }
  }
})

router.afterEach(to => {
  document.title = `${String(to.meta.title || '首页')} - 时迁校园内容社区`
})

export default router
