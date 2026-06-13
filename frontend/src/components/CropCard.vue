<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { CsvRow } from '@/api/static'
import {
  CROP_CATEGORY_LABEL,
  getCropCategory,
} from '@/utils/cropType'
import { formatHarvestTime, parseHarvestHours } from '@/utils/harvestTime'
import { useCropImage, handleCropImageError, CROP_CARD_FRAME_HEIGHT, CROP_CARD_IMAGE_HEIGHT } from '@/utils/cropImage'

const props = defineProps<{
  crop: CsvRow
}>()

const router = useRouter()

const name = computed(() => String(props.crop['name'] ?? ''))
const category = computed(() => getCropCategory(props.crop))

const { imageUrl: cropImage } = useCropImage(() => name.value)

const cropFrameHeight = `${CROP_CARD_FRAME_HEIGHT}px`
const cropImageHeight = `${CROP_CARD_IMAGE_HEIGHT}px`

const sellPerHour = computed(() => {
  const hours = parseHarvestHours(props.crop['harvest_time'])
  const sell = Number(props.crop['total_sell_price'] ?? 0)
  return hours > 0 ? (sell / hours).toFixed(1) : '0'
})

const expPerHour = computed(() => {
  const hours = parseHarvestHours(props.crop['harvest_time'])
  const exp = Number(props.crop['exp_gain'] ?? 0)
  return hours > 0 ? (exp / hours).toFixed(1) : '0'
})

function goDetail() {
  router.push(`/encyclopedia/${encodeURIComponent(name.value)}`)
}
</script>

<template>
  <div class="crop-card" @click="goDetail">
    <div :class="['crop-image', category]">
      <img
        :src="cropImage"
        :alt="name"
        class="crop-img"
        @error="handleCropImageError($event, name)"
      />
    </div>
    <div class="crop-body">
      <div class="crop-header">
        <h3>{{ name }}</h3>
        <span :class="['crop-tag', category]">{{ CROP_CATEGORY_LABEL[category] }}</span>
      </div>
      <div class="crop-meta">
        <span class="meta-level">Lv.{{ crop['unlock_level'] }}</span>
        <span class="meta-time">{{ formatHarvestTime(crop['harvest_time']) }}</span>
      </div>
      <div class="crop-rates">
        <span class="rate-coin">{{ sellPerHour }}金币/h</span>
        <span class="rate-exp">{{ expPerHour }}经验/h</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.crop-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: 0 2px 10px rgba(27, 67, 50, 0.08);
}

.crop-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(27, 67, 50, 0.14);
}

.crop-image {
  height: v-bind(cropFrameHeight);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

/* 渐变背景层 */
.crop-image::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
}

.crop-image.exp::before {
  background: linear-gradient(135deg, #e6fffb, #87e8de);
}

.crop-image.high_coin::before {
  background: linear-gradient(135deg, #fff7e6, #ffd591);
}

.crop-image.hero::before {
  background: linear-gradient(135deg, #f9f0ff, #d3adf7);
}

/* 承载框内固定较小高度，等比缩放并居中 */
.crop-img {
  position: relative;
  z-index: 1;
  height: v-bind(cropImageHeight);
  width: auto;
  max-width: 100%;
  object-fit: contain;
  display: block;
}

.crop-body {
  padding: 14px 16px;
}

.crop-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.crop-header h3 {
  margin: 0;
  font-size: 16px;
}

.crop-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 13px;
  color: var(--fh-muted);
  margin-bottom: 8px;
}

.crop-rates {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 13px;
  font-weight: 500;
}

.rate-coin {
  color: #d48806;
}

.rate-exp {
  color: #389e0d;
}

.crop-tag {
  display: inline-block;
  height: 20px;
  line-height: 20px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
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
</style>
