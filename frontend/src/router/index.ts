import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: '首页' },
    },
    {
      path: '/data',
      name: 'data',
      component: () => import('@/views/data/DataView.vue'),
      meta: { title: '农场数据' },
    },
    {
      path: '/encyclopedia',
      name: 'encyclopedia',
      component: () => import('@/views/encyclopedia/EncyclopediaView.vue'),
      meta: { title: '农作物图鉴' },
    },
    {
      path: '/encyclopedia/:name',
      name: 'crop-detail',
      component: () => import('@/views/encyclopedia/CropDetailView.vue'),
      meta: { title: '作物详情' },
    },
    {
      path: '/calculator/maturity',
      name: 'maturity-calculator',
      component: () => import('@/views/calculator/MaturityCalculatorView.vue'),
      meta: { title: '成熟时间计算' },
    },
    {
      path: '/account',
      name: 'account',
      component: () => import('@/views/account/AccountView.vue'),
      meta: { title: '我的账号' },
    },
  ],
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || '王者荣耀农场助手'
  document.title = `${title} · 王者荣耀农场助手`
})

export default router
