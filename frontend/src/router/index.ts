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
    { path: '/', component: () => import('../views/HomeView.vue'), meta: { title: '首页', requiresAuth: true } },
    { path: '/goals', component: () => import('../views/GoalsView.vue'), meta: { title: '人生千事', requiresAuth: true } },
    ...navigation.filter(item => item.path === '/settings').map(({ path, title }) => ({
      path, component: PlaceholderView, meta: { title },
    })),
    { path: '/goals/:slotNo', component: () => import('../views/GoalDetailView.vue'), meta: { title: '事项详情', requiresAuth: true } },
    { path: '/timeline', component: () => import('../views/TimelineView.vue'), meta: { title: '时间轴', requiresAuth: true } },
    { path: '/quotes', component: () => import('../views/QuotesView.vue'), meta: { title: '金句收藏', requiresAuth: true } },
    { path: '/stats', component: () => import('../views/StatsView.vue'), meta: { title: '数据统计', requiresAuth: true } },
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(to => {
  if (to.meta.requiresAuth && !getToken()) return { path: '/login', query: { redirect: to.fullPath } }
})
window.addEventListener('life1000:unauthorized', () => {
  if (router.currentRoute.value.path !== '/login') void router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
})
router.afterEach(to => {
  document.title = `${to.meta.title} · Life1000`
})

export function safeReturnPath(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return '/'
  const resolved = router.resolve(value)
  return /^\/(goals(\/\d+)?|timeline|stats|quotes|settings)?$/.test(resolved.path) ? resolved.fullPath : '/'
}