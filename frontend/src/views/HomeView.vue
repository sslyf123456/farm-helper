<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'

const router = useRouter()

const features = [
  {
    title: '农场数据',
    desc: '查阅升级表、小摊、土地、农作物与培育度等官方数值',
    path: '/data',
    color: '#40916c',
  },
  {
    title: '农作物图鉴',
    desc: '大版面浏览每种作物的详细属性与分类',
    path: '/encyclopedia',
    color: '#52b788',
  },
  {
    title: '成熟时间计算',
    desc: '按不同浇水策略计算作物实际成熟时间',
    path: '/calculator/maturity',
    color: '#2d6a4f',
  },
  {
    title: '我的账号',
    desc: '管理王者账号、区服与农场档案（本地暂存）',
    path: '/account',
    color: '#1b4332',
  },
]
</script>

<template>
  <div class="home">
    <section class="hero page-card">
      <h1 class="page-title">王者荣耀农场助手</h1>
      <p class="page-desc">
        个人农场账号数据录入与整理、农场数据展示、成熟时间计算 —— 你的农场攻略小帮手。
      </p>
    </section>

    <div class="feature-grid">
      <div
        v-for="item in features"
        :key="item.path"
        class="feature-card page-card"
        @click="router.push(item.path)"
      >
        <div class="feature-dot" :style="{ background: item.color }" />
        <h3>{{ item.title }}</h3>
        <p>{{ item.desc }}</p>
        <el-button type="primary" link class="enter-btn">
          进入 <el-icon class="enter-icon"><ArrowRight /></el-icon>
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hero {
  margin-bottom: 24px;
  background: linear-gradient(135deg, #fff 0%, #f0faf4 100%);
}

.feature-card {
  background: #ffffff;
  position: relative;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  padding-bottom: 56px;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}

.feature-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(27, 67, 50, 0.12);
}

.feature-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-bottom: 12px;
}

.feature-card h3 {
  margin: 0 0 8px;
  font-size: 18px;
}

.feature-card p {
  margin: 0 0 12px;
  color: var(--fh-muted);
  font-size: 14px;
  line-height: 1.6;
}

.enter-btn {
  position: absolute;
  right: 24px;
  bottom: 20px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.enter-icon {
  margin-left: 0;
}
</style>
