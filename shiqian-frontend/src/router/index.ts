import { createRouter, createWebHashHistory, RouteRecordRaw } from 'vue-router'
import { useAppStore } from '@/stores/app'
import HomeView from '@/views/user/HomeView.vue'
import PlazaView from '@/views/user/PlazaView.vue'
import CategoriesView from '@/views/user/CategoriesView.vue'
import PublishView from '@/views/user/PublishView.vue'
import FavoritesView from '@/views/user/FavoritesView.vue'
import MineView from '@/views/user/MineView.vue'
import ProfileView from '@/views/user/ProfileView.vue'
import DetailView from '@/views/user/DetailView.vue'
import LoginView from '@/views/user/LoginView.vue'
import RegisterView from '@/views/user/RegisterView.vue'
import AdminHomeView from '@/views/admin/AdminHomeView.vue'
import AuditView from '@/views/admin/AuditView.vue'
import ResourceAdminView from '@/views/admin/ResourceAdminView.vue'
import RecycleBinView from '@/views/admin/RecycleBinView.vue'
import CategoryAdminView from '@/views/admin/CategoryAdminView.vue'
import UserAdminView from '@/views/admin/UserAdminView.vue'

const adminMeta = { requiresAuth: true, roles: ['admin'] }

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/home' },
  { path: '/home', component: HomeView, meta: { title: '首页' } },
  { path: '/plaza', component: PlazaView, meta: { title: '资源广场' } },
  { path: '/categories', component: CategoriesView, meta: { title: '分类浏览' } },
  { path: '/publish', component: PublishView, meta: { title: '发布资源', requiresAuth: true } },
  { path: '/favorites', component: FavoritesView, meta: { title: '我的收藏', requiresAuth: true } },
  { path: '/mine', component: MineView, meta: { title: '我的发布', requiresAuth: true } },
  { path: '/profile', component: ProfileView, meta: { title: '个人资料', requiresAuth: true } },
  { path: '/detail/:id', component: DetailView, meta: { title: '资源详情' } },
  { path: '/login', component: LoginView, meta: { title: '登录' } },
  { path: '/register', component: RegisterView, meta: { title: '注册' } },
  { path: '/admin', component: AdminHomeView, meta: { ...adminMeta, title: '后台首页' } },
  { path: '/admin/audit', component: AuditView, meta: { ...adminMeta, title: '资源审核' } },
  { path: '/admin/resources', component: ResourceAdminView, meta: { ...adminMeta, title: '资源管理' } },
  { path: '/admin/recycle-bin', component: RecycleBinView, meta: { ...adminMeta, title: '回收站' } },
  { path: '/admin/categories', component: CategoryAdminView, meta: { ...adminMeta, title: '分类管理' } },
  { path: '/admin/users', component: UserAdminView, meta: { ...adminMeta, title: '用户管理' } },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async to => {
  const store = useAppStore()
  const requiresAuth = Boolean(to.meta.requiresAuth)
  const roles = to.meta.roles as string[] | undefined
  const isAdminRoute = to.path === '/admin' || to.path.startsWith('/admin/')

  if ((requiresAuth || isAdminRoute) && !store.logged) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if ((requiresAuth || isAdminRoute) && store.logged && !store.currentUser) {
    try {
      await store.loadCurrentUser()
    } catch {
      store.logout()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  const backendRole = store.currentUser?.role
  if (isAdminRoute && backendRole !== 'ADMIN') {
    return { path: '/home' }
  }

  if (roles?.includes('admin') && backendRole !== 'ADMIN') {
    return { path: '/home' }
  }
})

router.afterEach(to => {
  document.title = `${String(to.meta.title || '首页')} - 时迁校园资源共享平台`
})

export default router
