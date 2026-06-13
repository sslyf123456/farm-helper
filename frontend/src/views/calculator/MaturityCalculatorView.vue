<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchCrops, type CsvRow } from '@/api/static'
import {
  WATER_STRATEGIES,
  calcMaturitySeconds,
  formatDuration,
  parseHarvestTime,
} from '@/utils/harvestTime'

const crops = ref<CsvRow[]>([])
const selectedCrop = ref('')
const customTimeText = ref('')
const plantTime = ref<Date | null>(null)
const usePlantTime = ref(false)

onMounted(async () => {
  try {
    crops.value = await fetchCrops()
  } catch {
    /* 允许手动输入时间 */
  }
})

const baseSeconds = computed(() => {
  if (selectedCrop.value) {
    const crop = crops.value.find((c) => c['name'] === selectedCrop.value)
    if (crop) return parseHarvestTime(crop['harvest_time'] ?? '')
  }
  if (customTimeText.value.trim()) {
    return parseHarvestTime(customTimeText.value.trim())
  }
  return null
})

const results = computed(() => {
  if (baseSeconds.value == null) return []
  return WATER_STRATEGIES.map((s) => {
    const seconds = calcMaturitySeconds(baseSeconds.value!, s.key)
    let matureAt: string | null = null
    if (usePlantTime.value && plantTime.value) {
      const t = new Date(plantTime.value.getTime() + seconds * 1000)
      matureAt = t.toLocaleString('zh-CN')
    }
    return { ...s, seconds, formatted: formatDuration(seconds), matureAt }
  })
})

function onCropChange(name: string) {
  selectedCrop.value = name
  const crop = crops.value.find((c) => c['name'] === name)
  if (crop) customTimeText.value = crop['harvest_time'] ?? ''
}
</script>

<template>
  <div>
    <div class="page-card form-section">
      <h1 class="page-title">成熟时间计算</h1>
      <p class="page-desc">根据浇水策略计算作物实际成熟所需时间</p>
      <el-form label-width="120px">
        <el-form-item label="选择作物">
          <el-select
            v-model="selectedCrop"
            filterable
            clearable
            placeholder="从列表选择"
            style="width: 280px"
            @change="onCropChange"
          >
            <el-option
              v-for="c in crops"
              :key="c['name']"
              :label="`${c['name']}（${c['harvest_time']}）`"
              :value="c['name']"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="或输入时间">
          <el-input
            v-model="customTimeText"
            placeholder="如 30s、2min、1h、8h、16h、32h"
            style="width: 280px"
            @input="selectedCrop = ''"
          />
        </el-form-item>
        <el-form-item label="种下时间">
          <el-switch v-model="usePlantTime" />
          <el-date-picker
            v-if="usePlantTime"
            v-model="plantTime"
            type="datetime"
            placeholder="选择种下时间"
            style="margin-left: 12px"
          />
        </el-form-item>
      </el-form>
    </div>

    <el-alert
      v-if="baseSeconds == null"
      title="请选择作物或输入有效的收获时间"
      type="info"
      show-icon
      :closable="false"
      class="mb"
    />

    <div v-else class="result-grid">
      <div v-for="item in results" :key="item.key" class="result-card page-card">
        <h3>{{ item.label }}</h3>
        <p class="time">{{ item.formatted }}</p>
        <p class="desc">{{ item.desc }}</p>
        <p v-if="item.matureAt" class="mature-at">预计成熟：{{ item.matureAt }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.form-section {
  margin-bottom: 20px;
}

.mb {
  margin-bottom: 16px;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.result-card h3 {
  margin: 0 0 8px;
  font-size: 16px;
  color: var(--fh-green);
}

.time {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 700;
}

.desc {
  margin: 0;
  font-size: 13px;
  color: var(--fh-muted);
}

.mature-at {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--fh-green-light);
}
</style>
