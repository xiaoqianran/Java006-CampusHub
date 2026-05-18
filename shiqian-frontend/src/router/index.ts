import { createRouter, createWebHashHistory, RouteRecordRaw } from 'vue-router'
import HomeView from '@/views/user/HomeView.vue'
import PlazaView from '@/views/user/PlazaView.vue'
import CategoriesView from '@/views/user/CategoriesView.vue'
import PublishView from '@/views/user/PublishView.vue'
import FavoritesView from '@/views/user/FavoritesView.vue'
import MineView from '@/views/user/MineView.vue'
import DetailView from '@/views/user/DetailView.vue'
import LoginView from '@/views/user/LoginView.vue'
import RegisterView from '@/views/user/RegisterView.vue'
import AdminHomeView from '@/views/admin/AdminHomeView.vue'
import AuditView from '@/views/admin/AuditView.vue'
import ResourceAdminView from '@/views/admin/ResourceAdminView.vue'
import CategoryAdminView from '@/views/admin/CategoryAdminView.vue'
import UserAdminView from '@/views/admin/UserAdminView.vue'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/home' },
  { path: '/home', component: HomeView, meta: { title: '首页' } },
  { path: '/plaza', component: PlazaView, meta: { title: '资源广场' } },
  { path: '/categories', component: CategoriesView, meta: { title: '分类浏览' } },
  { path: '/publish', component: PublishView, meta: { title: '发布资源' } },
  { path: '/favorites', component: FavoritesView, meta: { title: '我的收藏' } },
  { path: '/mine', component: MineView, meta: { title: '我的发布' } },
  { path: '/detail/:id', component: DetailView, meta: { title: '资源详情' } },
  { path: '/login', component: LoginView, meta: { title: '登录' } },
  { path: '/register', component: RegisterView, meta: { title: '注册' } },
  { path: '/admin', component: AdminHomeView, meta: { title: '后台首页' } },
  { path: '/audit', component: AuditView, meta: { title: '资源审核' } },
  { path: '/resource-admin', component: ResourceAdminView, meta: { title: '资源管理' } },
  { path: '/category-admin', component: CategoryAdminView, meta: { title: '分类管理' } },
  { path: '/user-admin', component: UserAdminView, meta: { title: '用户管理' } }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.afterEach(to => {
  document.title = `${String(to.meta.title || '首页')} - 时迁校园资源共享平台`
})

export default router
