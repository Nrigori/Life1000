import { createRouter, createWebHistory } from 'vue-router'
import PlaceholderView from '../views/PlaceholderView.vue'

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
    ...navigation.map(({ path, title }) => ({
      path, component: PlaceholderView, meta: { title },
    })),
    { path: '/goals/:slotNo', component: PlaceholderView, meta: { title: '事项详情' } },
    { path: '/login', component: PlaceholderView, meta: { title: '登录' } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach((to) => {
  document.title = `${to.meta.title} · Life1000`
})
