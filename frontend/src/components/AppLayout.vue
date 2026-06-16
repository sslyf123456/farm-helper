<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Grid, Collection, Timer, User } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)
</script>

<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="brand" @click="router.push('/')">
        <img src="/favicon.svg" alt="" class="brand-icon" />
        <span class="brand-text">王者荣耀农场助手</span>
      </div>
      <el-menu
        mode="horizontal"
        :ellipsis="false"
        :default-active="activeMenu"
        router
        class="nav-menu"
      >
        <el-menu-item index="/data">
          <el-icon><Grid /></el-icon>
          农场数据
        </el-menu-item>
        <el-menu-item index="/encyclopedia">
          <el-icon><Collection /></el-icon>
          农作物图鉴
        </el-menu-item>
        <el-menu-item index="/calculator/maturity">
          <el-icon><Timer /></el-icon>
          收菜计算器
        </el-menu-item>
        <el-menu-item index="/account">
          <el-icon><User /></el-icon>
          我的账号
        </el-menu-item>
      </el-menu>
    </el-header>
    <el-main class="main">
      <router-view />
    </el-main>
    <el-footer class="footer">王者荣耀农场助手 · 个人数据整理工具</el-footer>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  overflow-x: visible;
  overflow-y: visible;
}

.header {
  display: flex;
  align-items: center;
  gap: 24px;
  background: #fff;
  border-bottom: 1px solid #e8efe9;
  padding: 0 24px;
  height: 60px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex-shrink: 0;
  user-select: none;
}

.brand-icon {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
}

.brand-text {
  font-size: 17px;
  font-weight: 700;
  color: var(--fh-green);
  white-space: nowrap;
}

.nav-menu {
  flex: 1;
  border-bottom: none;
  background: transparent;
}

.main {
  max-width: 1280px;
  width: 100%;
  margin: 0 auto;
  padding: 24px 20px 40px;
  /* 覆盖 el-main 默认 overflow:auto，避免与浏览器底部出现双横向滚动条 */
  overflow-x: visible;
  overflow-y: visible;
}

.footer {
  text-align: center;
  color: var(--fh-muted);
  font-size: 13px;
  background: transparent;
  height: 48px;
  line-height: 48px;
}
</style>
