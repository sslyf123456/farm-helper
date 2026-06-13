<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import CropCard from '@/components/CropCard.vue'
import { fetchCrops, type CsvRow } from '@/api/static'

const crops = ref<CsvRow[]>([])
const loading = ref(false)
const keyword = ref('')
const categoryFilter = ref('all')

onMounted(async () => {
  loading.value = true
  try {
    crops.value = await fetchCrops()
  } catch {
    ElMessage.error('数据加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
})

const filteredCrops = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return crops.value.filter((c) => {
    const name = String(c['name'] ?? '').toLowerCase()
    if (kw && !name.includes(kw)) return false

    const timeSeconds = Number(c['harvest_time'] ?? 0)
    const hero = String(c['mutation_hero'] ?? '').trim()
    const exp = Number(c['exp_gain'] ?? 0)

    if (categoryFilter.value === 'hero') return timeSeconds === 115200 || !!hero
    if (categoryFilter.value === 'high_coin') return timeSeconds === 57600 && exp === 1
    if (categoryFilter.value === 'exp') {
      return !(timeSeconds === 115200 || hero) && !(timeSeconds === 57600 && exp === 1)
    }
    return true
  })
})
</script>

<template>
  <div>
    <div class="page-card header-card">
      <h1 class="page-title">农作物图鉴</h1>
      <p class="page-desc">浏览每种作物的详细属性，点击进入详情页</p>

      <div class="toolbar-inner">
        <el-input v-model="keyword" placeholder="搜索作物名称" clearable style="max-width: 240px" />
        <el-radio-group v-model="categoryFilter">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="exp">经验作物</el-radio-button>
          <el-radio-button value="high_coin">高农场币</el-radio-button>
          <el-radio-button value="hero">英雄作物</el-radio-button>
        </el-radio-group>
        <span class="count">共 {{ filteredCrops.length }} 种</span>
      </div>
    </div>

    <div v-loading="loading" class="crop-grid">
      <CropCard v-for="crop in filteredCrops" :key="String(crop['name'])" :crop="crop" />
    </div>
  </div>
</template>

<style scoped>
.header-card {
  margin-bottom: 20px;
}

.toolbar-inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
}

.count {
  margin-left: auto;
  color: var(--fh-muted);
  font-size: 13px;
}

.crop-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 20px;
  min-height: 120px;
}
</style>
