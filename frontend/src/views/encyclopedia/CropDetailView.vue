<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchCropDetail, fetchCultivation, type CsvRow } from '@/api/static'
import {
  CROP_CATEGORY_LABEL,
  getCropCategory,
} from '@/utils/cropType'
import { formatHarvestTime, parseHarvestHours } from '@/utils/harvestTime'
import { useCropImage, handleCropImageError, CROP_DETAIL_FRAME_HEIGHT, CROP_DETAIL_IMAGE_HEIGHT } from '@/utils/cropImage'
import { ArrowLeft } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const crop = ref<CsvRow | null>(null)
const cultivation = ref<CsvRow | null>(null)
const loading = ref(false)

const cropName = computed(() => decodeURIComponent(route.params.name as string))
const category = computed(() => (crop.value ? getCropCategory(crop.value) : 'exp'))

const { imageUrl: cropImage } = useCropImage(() => cropName.value)

const detailFrameHeight = `${CROP_DETAIL_FRAME_HEIGHT}px`
const detailImageHeight = `${CROP_DETAIL_IMAGE_HEIGHT}px`

const detailFields = [
  { key: 'unlock_level', label: '解锁等级' },
  { key: 'seed_price', label: '种子价格' },
  { key: 'yield_qty', label: '产量' },
  { key: 'total_sell_price', label: '基础总售价' },
  { key: 'total_sell_price_per_hour', label: '每小时金币' },
  { key: 'exp_gain', label: '收获经验' },
  { key: 'exp_gain_per_hour', label: '每小时经验' },
  { key: 'harvest_time', label: '收获时间' },
  { key: 'mutation_limit', label: '变异上限' },
  { key: 'mutation_hero', label: '变异英雄' },
]

// 格式化字段值
const formatFieldValue = (field: { key: string }): string => {
  if (!crop.value) return '--'
  
  const value = crop.value[field.key]
  
  if (field.key === 'harvest_time') {
    return formatHarvestTime(value)
  }
  
  if (field.key === 'total_sell_price_per_hour') {
    const totalPrice = Number(crop.value['total_sell_price'] ?? 0)
    const hours = parseHarvestHours(crop.value['harvest_time'])
    if (hours > 0 && totalPrice > 0) {
      return (totalPrice / hours).toFixed(2)
    }
    return '--'
  }
  
  if (field.key === 'exp_gain_per_hour') {
    const expGain = Number(crop.value['exp_gain'] ?? 0)
    const hours = parseHarvestHours(crop.value['harvest_time'])
    if (hours > 0 && expGain > 0) {
      return (expGain / hours).toFixed(2)
    }
    return '--'
  }
  
  if (value === null || value === undefined || value === '') return '--'
  return String(value)
}

const cultivationLevels = computed(() => {
  if (!cultivation.value) return []
  
  const levels = ['lv2', 'lv3', 'lv4', 'lv5', 'lv6', 'lv7', 'lv8', 'lv9', 'lv10']
  let cumulative = 0
  let foundEmpty = false
  
  return levels.map(lv => {
    const rawValue = cultivation.value![lv]
    
    if (foundEmpty || rawValue === null || rawValue === undefined || rawValue === '') {
      foundEmpty = true
      return {
        level: lv.replace('lv', ''),
        amount: null,
        cumulative: null
      }
    }
    
    const amount = Number(rawValue)
    cumulative += amount
    
    return {
      level: lv.replace('lv', ''),
      amount,
      cumulative
    }
  })
})

onMounted(async () => {
  loading.value = true
  try {
    const [cropData, cultData] = await Promise.all([
      fetchCropDetail(cropName.value),
      fetchCultivation(cropName.value),
    ])
    crop.value = cropData
    cultivation.value = cultData[0] ?? null
  } catch {
    ElMessage.error('数据加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading">
    <el-button link type="primary" @click="router.push('/encyclopedia')"><el-icon><ArrowLeft /></el-icon> 返回图鉴</el-button>

    <template v-if="crop">
      <div class="detail-layout">
        <div v-if="crop" :class="['detail-image', 'page-card', category]">
          <img
            :src="cropImage"
            :alt="cropName"
            class="crop-img"
            @error="handleCropImageError($event, cropName)"
          />
        </div>

        <div class="detail-info page-card">
          <div class="title-row">
            <h1 class="page-title">{{ crop['name'] }}</h1>
            <span :class="['crop-tag', category]">{{ CROP_CATEGORY_LABEL[category] }}</span>
          </div>

          <el-descriptions :column="2" border>
            <el-descriptions-item
              v-for="field in detailFields"
              :key="field.key"
              :label="field.label"
            >
              {{ formatFieldValue(field) }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <div v-if="cultivation" class="page-card cult-section">
        <h2>培育度</h2>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="升至满级合计">{{ cultivation['total'] }}</el-descriptions-item>
          <el-descriptions-item
            v-for="item in cultivationLevels"
            :key="item.level"
            :label="'升至 ' + item.level + ' 级'"
          >
            {{ item.amount === null ? '--' : `${item.amount}（累计 ${item.cumulative}）` }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 20px;
  margin-top: 16px;
}

.detail-image {
  display: flex;
  align-items: center;
  justify-content: center;
  height: v-bind(detailFrameHeight);
  overflow: hidden;
  position: relative;
}

/* 渐变背景层 */
.detail-image::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
}

.detail-image.exp::before {
  background: linear-gradient(160deg, #e6fffb, #b2f5ea);
}

.detail-image.high_coin::before {
  background: linear-gradient(160deg, #fff7e6, #ffe7ba);
}

.detail-image.hero::before {
  background: linear-gradient(160deg, #f9f0ff, #efdbff);
}

/* 承载框内固定较小高度，等比缩放并居中 */
.crop-img {
  position: relative;
  z-index: 1;
  height: v-bind(detailImageHeight);
  width: auto;
  max-width: 100%;
  object-fit: contain;
  display: block;
}

/* 详情描述区域配色与边框 */
.detail-info :deep(.el-descriptions__body) {
  background: #f7fbfd;
}

.detail-info :deep(.el-descriptions__table) {
  border-collapse: separate;
  border-spacing: 0;
  border: 2px solid #cfe2ec;
  border-radius: 8px;
  overflow: hidden;
}

.detail-info :deep(.el-descriptions__label) {
  background: #dceef7;
  color: #2f6690;
  font-weight: 500;
  font-size: 15px;
}

.detail-info :deep(.el-descriptions__content) {
  background: #eaf3f8;
  color: #234e6b;
  font-size: 15px;
}

/* 单元格只画右边框和下边框，避免与相邻单元格叠加变粗 */
.detail-info :deep(.el-descriptions__cell) {
  border: none !important;
  border-right: 2px solid #cfe2ec !important;
  border-bottom: 2px solid #cfe2ec !important;
}

/* 最后一列/最后一行去掉边框，由表格外框统一收口 */
.detail-info :deep(.el-descriptions__table tr .el-descriptions__cell:last-child) {
  border-right: none !important;
}

.detail-info :deep(.el-descriptions__table tr:last-child .el-descriptions__cell) {
  border-bottom: none !important;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.title-row .page-title {
  margin: 0;
}

.cult-section {
  margin-top: 20px;
}

.cult-section h2 {
  margin: 0 0 16px;
  font-size: 18px;
}

.cult-section :deep(.el-descriptions) {
  max-width: 500px;
}

.cult-section :deep(.el-descriptions__label),
.cult-section :deep(.el-descriptions__content) {
  font-size: 15px;
}

.cult-section :deep(.el-descriptions__body) {
  background: #eaf3f8;
}

.cult-section :deep(.el-descriptions__table) {
  border-collapse: separate;
  border-spacing: 0;
  border: 2px solid #cfe2ec;
  border-radius: 8px;
  overflow: hidden;
}

.cult-section :deep(.el-descriptions__label) {
  background: #dceef7;
  color: #2f6690;
  font-weight: 500;
}

.cult-section :deep(.el-descriptions__content) {
  background: #eaf3f8;
  color: #234e6b;
}

.cult-section :deep(.el-descriptions__cell) {
  border: none !important;
  border-right: 2px solid #cfe2ec !important;
  border-bottom: 2px solid #cfe2ec !important;
}

.cult-section :deep(.el-descriptions__table tr .el-descriptions__cell:last-child) {
  border-right: none !important;
}

.cult-section :deep(.el-descriptions__table tr:last-child .el-descriptions__cell) {
  border-bottom: none !important;
}

.crop-tag {
  display: inline-block;
  height: 24px;
  line-height: 24px;
  padding: 0 10px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  color: #fff;
  vertical-align: middle;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

.crop-tag.exp {
  background: linear-gradient(135deg, rgba(178, 235, 227, 0.9), rgba(19, 194, 173, 0.9));
}

.crop-tag.high_coin {
  background: linear-gradient(135deg, rgba(250, 219, 20, 0.9), rgba(212, 136, 6, 0.9));
}

.crop-tag.hero {
  background: linear-gradient(135deg, rgba(74, 144, 217, 0.9), rgba(142, 68, 173, 0.9));
}

.note {
  margin-top: 12px;
  font-size: 13px;
  color: var(--fh-muted);
}

@media (max-width: 768px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
}
</style>
