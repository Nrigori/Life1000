import { createRouter, createWebHistory } from 'vue-router'
import PlaceholderView from '../views/PlaceholderView.vue'
import { getToken } from '../api/session'

export const navigation = [
  { path: '/goals', title: '人生千事' },
  { path: '/timeline', title: '时间轴' },
  { path: '/stats', title: '数据统计' },
  { path: '/quotes', title: '金句收藏' },
  { path: '/settings', title: '设置' },
]

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: PlaceholderView, meta: { title: '首页' } },
    { path: '/goals', component: () => import('../views/GoalsView.vue'), meta: { title: '人生千事', requiresAuth: true } },
    ...navigation.filter(item => item.path !== '/goals').map(({ path, title }) => ({
      path, component: PlaceholderView, meta: { title },
    })),
    { path: '/goals/:slotNo', component: () => import('../views/GoalDetailPlaceholderView.vue'), meta: { title: '事项详情', requiresAuth: true } },
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(to => {
  if (to.meta.requiresAuth && !getToken()) return '/login'
})
window.addEventListener('life1000:unauthorized', () => {
  if (router.currentRoute.value.path !== '/login') void router.replace('/login')
})
router.afterEach(to => {
  document.title = `${to.meta.title} · Life1000`
})
