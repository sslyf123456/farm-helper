<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { InfoFilled, AlarmClock } from '@element-plus/icons-vue'
import { Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchCrops, type CsvRow } from '@/api/static'
import { 
  calculateAllStrategies, 
  calculateAllStrategiesReverse,
  calculateAllStrategiesReverseByMatureTime,
  type WateringResponse 
} from '@/api/calculator'
import {
  formatDuration,
  formatHarvestTime,
  parseHarvestTime,
} from '@/utils/harvestTime'

/** 常见收获时间（秒）：1h, 8h, 16h, 32h */
const COMMON_TIMES = [3600, 28800, 57600, 115200]

const crops = ref<CsvRow[]>([])
const selectedCropName = ref('')
const selectedTimeSeconds = ref<number | null>(3600)
const plantTime = ref<Date>(new Date())
/** 时间卡片模式：common = 常见, all = 所有 */
const timeMode = ref<'common' | 'all'>('common')
/** 计算原理弹窗 */
const showMechanism = ref(false)
/** 计算结果 */
const calculationResults = ref<WateringResponse[]>([])
/** 选中的策略（用于过滤显示） */
const selectedStrategy = ref<string>('extreme')

/** 计算模式：forward = 正向（从种植时间计算），reverse = 反向（从剩余成熟时间计算） */
const calculationMode = ref<'forward' | 'reverse'>('forward')

/** 反向计算输入：输入类型（remaining = 剩余成熟时间，matureTime = 成熟时间） */
const reverseInputType = ref<'remaining' | 'matureTime'>('remaining')
/** 反向计算：剩余成熟时间（小时） */
const remainingHours = ref<number | null>(0)
/** 反向计算：剩余成熟时间（分钟） */
const remainingMinutes = ref<number | null>(0)
/** 反向计算：成熟时间 */
const matureTime = ref<Date | null>(null)
/** 反向计算：水分维持度（小时） */
const moistureHours = ref<number | null>(0)
/** 反向计算：水分维持度（分钟） */
const moistureMinutes = ref<number | null>(0)
/** 反向计算：当前时间（默认为现在） */
const currentTime = ref<Date>(new Date())

onMounted(async () => {
  plantTime.value = new Date()
  currentTime.value = new Date()
  try {
    crops.value = await fetchCrops()
  } catch {
    /* 加载失败时作物列表为空 */
  }
})

/** 当前显示的时间卡片列表 */
const displayTimes = computed(() => {
  const allSet = new Set<number>()
  for (const c of crops.value) {
    const s = parseHarvestTime(c['harvest_time'])
    if (s != null) allSet.add(s)
  }
  const all = Array.from(allSet).sort((a, b) => a - b)

  if (timeMode.value === 'common') {
    return all.filter((s) => COMMON_TIMES.includes(s))
  }
  return all
})

/** 每个时间对应的作物数量（全部数据） */
const timeCropCounts = computed(() => {
  const map = new Map<number, number>()
  for (const c of crops.value) {
    const s = parseHarvestTime(c['harvest_time'])
    if (s != null) {
      map.set(s, (map.get(s) || 0) + 1)
    }
  }
  return map
})

/** 过滤后的作物列表（用于下拉框），取决于 timeMode */
const filteredCrops = computed(() => {
  if (timeMode.value === 'all') return crops.value
  return crops.value.filter((c) => {
    const s = parseHarvestTime(c['harvest_time'])
    return s != null && COMMON_TIMES.includes(s)
  })
})

/** 当前选中的作物对象 */
const selectedCrop = computed(() =>
  crops.value.find((c) => c['name'] === selectedCropName.value) ?? null,
)

/** 作物选中后，自动同步时间卡片并锁定 */
watch(selectedCrop, (crop) => {
  if (crop) {
    const s = parseHarvestTime(crop['harvest_time'])
    selectedTimeSeconds.value = s
  }
})

/** 切换模式时 */
watch(timeMode, (mode) => {
  if (mode === 'common') {
    // 从所有切回常见：若已选作物且其时间不在常见列表中，清空作物
    if (selectedCrop.value) {
      const s = parseHarvestTime(selectedCrop.value['harvest_time'])
      if (s != null && !COMMON_TIMES.includes(s)) {
        selectedCropName.value = ''
        selectedTimeSeconds.value = null
        return
      }
    }
    // 未选作物：若选中时间不在常见列表里，清空时间选择
    if (selectedTimeSeconds.value != null && !COMMON_TIMES.includes(selectedTimeSeconds.value)) {
      selectedTimeSeconds.value = null
    }
  }
})

/** 切换输入方式时，清空另一种方式的输入内容 */
watch(reverseInputType, (mode) => {
  if (mode === 'remaining') {
    matureTime.value = null
  } else {
    remainingHours.value = 0
    remainingMinutes.value = 0
  }
  moistureHours.value = 0
  moistureMinutes.value = 0
})

/** 最终用于计算的基础秒数 */
const baseSeconds = computed(() => selectedTimeSeconds.value)

/** 计算剩余秒数（从小时和分钟输入），返回 null 表示未填写 */
const remainingSeconds = computed(() => {
  const hours = remainingHours.value
  const minutes = remainingMinutes.value
  if (hours == null || minutes == null) {
    return null
  }
  return hours * 3600 + minutes * 60
})

/** 计算水分维持度秒数（从小时和分钟输入），返回 null 表示未填写 */
const moistureSeconds = computed(() => {
  const hours = moistureHours.value
  const minutes = moistureMinutes.value
  if (hours == null || minutes == null) {
    return null
  }
  return hours * 3600 + minutes * 60
})

/** 手动触发计算 */
async function handleCalculate() {
  // 先进行所有验证，验证失败直接返回
  if (!baseSeconds.value || baseSeconds.value <= 0) {
    ElMessage.warning('请先选择成熟时间')
    calculationResults.value = []
    return
  }

  if (calculationMode.value === 'reverse') {
    if (reverseInputType.value === 'remaining') {
      // 剩余时间模式验证
      if (remainingSeconds.value == null || remainingSeconds.value < 0) {
        ElMessage.warning('请填写剩余成熟时间（小时和分钟）')
        calculationResults.value = []
        return
      }
      if (remainingSeconds.value > baseSeconds.value) {
        ElMessage.warning('剩余成熟时间不能超过作物周期')
        calculationResults.value = []
        return
      }
      if (moistureSeconds.value == null) {
        ElMessage.warning('请填写水分维持度（小时和分钟）')
        calculationResults.value = []
        return
      }
      if (moistureSeconds.value > baseSeconds.value / 3) {
        ElMessage.warning('水分维持度不能超过作物周期的三分之一')
        calculationResults.value = []
        return
      }
    } else {
      // 成熟时间模式验证
      if (!matureTime.value) {
        ElMessage.warning('请选择成熟时间')
        calculationResults.value = []
        return
      }
      if (!currentTime.value) {
        ElMessage.warning('请选择当前时间')
        calculationResults.value = []
        return
      }
      // 成熟时间 - 当前时间 不能超过作物周期
      const diffMs = matureTime.value.getTime() - currentTime.value.getTime()
      if (diffMs <= 0) {
        ElMessage.warning('成熟时间不能早于当前时间')
        calculationResults.value = []
        return
      }
      if (Math.floor(diffMs / 1000) > baseSeconds.value) {
        ElMessage.warning('成熟时间减当前时间不能超过作物周期')
        calculationResults.value = []
        return
      }
      if (moistureSeconds.value == null) {
        ElMessage.warning('请填写水分维持度（小时和分钟）')
        calculationResults.value = []
        return
      }
      if (moistureSeconds.value > baseSeconds.value / 3) {
        ElMessage.warning('水分维持度不能超过作物周期的三分之一')
        calculationResults.value = []
        return
      }
    }
  }

  // 验证通过后进行计算
  try {
    if (calculationMode.value === 'forward') {
      // 正向计算
      calculationResults.value = await calculateAllStrategies(
        baseSeconds.value,
        plantTime.value
      )
    } else {
      // 反向计算
      if (reverseInputType.value === 'remaining') {
        calculationResults.value = await calculateAllStrategiesReverse(
          baseSeconds.value,
          remainingSeconds.value!,
          moistureSeconds.value!,
          currentTime.value
        )
      } else {
        calculationResults.value = await calculateAllStrategiesReverseByMatureTime(
          baseSeconds.value,
          matureTime.value!,
          moistureSeconds.value!,
          currentTime.value
        )
      }
    }
  } catch (error) {
    console.error('计算失败:', error)
    calculationResults.value = []
  }
}

/** 选择时间卡片 */
function selectTime(seconds: number) {
  if (selectedCrop.value) return
  selectedTimeSeconds.value = seconds
}

/** 过滤后的显示结果 */
const filteredResults = computed(() => {
  return calculationResults.value.filter((result) =>
    result.strategy === selectedStrategy.value,
  )
})

/** 策略选项配置 */
const strategyOptions = [
  { value: 'none', label: '自然成熟' },
  { value: 'once', label: '佛系浇水' },
  { value: 'diligent', label: '勤奋浇水' },
  { value: 'extreme', label: '极限浇水' },
]

/** 搜索关键词 */
const searchQuery = ref('')

/** 根据搜索关键词过滤的作物列表 */
const searchFilteredCrops = computed(() => {
  if (!searchQuery.value) return filteredCrops.value
  const query = searchQuery.value.toLowerCase()
  return filteredCrops.value.filter((c) => {
    const name = String(c['name']).toLowerCase()
    return name.includes(query)
  })
})

/** 动态计算表单label宽度 */
const formLabelWidth = computed(() => {
  const labels = [
    '计算模式',
    '选择作物',
    '种下时间',
    '输入方式',
    '剩余时间',
    '成熟时间',
    '水分维持度',
    '当前时间'
  ]
  
  // 找出最长的label
  const maxLength = Math.max(...labels.map(l => l.length))
  
  // 每个中文字符按15px计算，加上30px的padding
  const width = maxLength * 15 + 30
  
  return `${width}px`
})
</script>

<template>
  <div class="harvest-calc">
    <!-- 顶部表单区 -->
    <div class="page-card form-section">
      <h1 class="page-title">收菜计算器</h1>
      <p class="page-desc">选择作物或直接点击成熟时间卡片，查看各浇水策略下的收菜时间</p>

      <el-form :label-width="formLabelWidth" label-position="left">
        <!-- 计算模式切换 -->
        <el-form-item label="计算模式">
          <el-radio-group v-model="calculationMode" size="default" class="mode-radio-group">
            <el-radio-button value="forward">正向计算</el-radio-button>
            <el-radio-button value="reverse">反向计算</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <!-- 作物选择（可选） -->
        <el-form-item label="选择作物">
          <el-select
            v-model="selectedCropName"
            filterable
            clearable
            placeholder="可选，不选时下方卡片可自由切换"
            style="width: 280px"
            @visible-change="(visible: boolean) => { if (!visible) searchQuery = '' }"
            :filter-method="(query: string) => { searchQuery = query }"
          >
            <el-option
              v-for="c in searchFilteredCrops"
              :key="String(c['name'])"
              :label="`Lv.${c['unlock_level']} ${c['name']}（${formatHarvestTime(c['harvest_time'])}）`"
              :value="String(c['name'])"
            />
          </el-select>
        </el-form-item>

        <!-- 正向计算模式的表单 -->
        <template v-if="calculationMode === 'forward'">
          <el-form-item label="种下时间">
            <el-date-picker
              v-model="plantTime"
              type="datetime"
              placeholder="选择种下时间"
              format="YYYY-MM-DD HH:mm"
              popper-class="compact-datetime-picker"
              style="width: 220px"
            />
          </el-form-item>
        </template>

        <!-- 反向计算模式的表单 -->
        <template v-else>
          <el-form-item label="输入方式">
            <el-radio-group v-model="reverseInputType" size="default" class="mode-radio-group">
              <el-radio-button value="remaining">剩余成熟时间</el-radio-button>
              <el-radio-button value="matureTime">成熟时间</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="reverseInputType === 'remaining'" label="剩余成熟时间">
            <div style="display: flex; gap: 8px; align-items: center;">
              <el-input
                v-model.number="remainingHours"
                style="width: 55px"
              />
              <span>小时</span>
              <el-input
                v-model.number="remainingMinutes"
                style="width: 55px"
              />
              <span>分钟</span>
            </div>
          </el-form-item>

          <el-form-item v-else label="成熟时间">
            <el-date-picker
              v-model="matureTime"
              type="datetime"
              placeholder="选择成熟时间"
              format="YYYY-MM-DD HH:mm"
              popper-class="compact-datetime-picker"
              style="width: 220px"
            />
          </el-form-item>

          <el-form-item label="水分维持度">
            <div style="display: flex; gap: 8px; align-items: center;">
              <el-input
                v-model.number="moistureHours"
                style="width: 55px"
              />
              <span>小时</span>
              <el-input
                v-model.number="moistureMinutes"
                style="width: 55px"
              />
              <span>分钟</span>
            </div>
          </el-form-item>

          <el-form-item label="当前时间">
            <el-date-picker
              v-model="currentTime"
              type="datetime"
              placeholder="选择当前时间"
              format="YYYY-MM-DD HH:mm"
              popper-class="compact-datetime-picker"
              style="width: 220px"
            />
          </el-form-item>
        </template>

        <!-- 计算按钮 -->
        <el-form-item>
          <button 
            type="button"
            class="calc-button"
            @click="handleCalculate"
          >
            <span class="button-text">开始计算</span>
          </button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 时间卡片区 -->
    <div class="section-title">
      <span>成熟时间</span>
      <span class="section-sub">（必选，点击卡片）</span>
      <div class="mode-switch">
        <el-radio-group v-model="timeMode" size="small" class="time-mode-group">
          <el-radio-button value="common">常见</el-radio-button>
          <el-radio-button value="all">所有</el-radio-button>
        </el-radio-group>
      </div>
      <span v-if="selectedCropName" class="locked-badge">
        已锁定为{{ selectedCropName }}
      </span>
      <span v-else-if="selectedTimeSeconds != null" class="selected-badge">
        已选：{{ formatDuration(selectedTimeSeconds) }}
      </span>
    </div>

    <div v-if="crops.length === 0" class="loading-tip">
      <el-icon class="is-loading"><Loading /></el-icon>
      正在加载数据...
    </div>

    <div v-else class="time-cards">
      <div
        v-for="sec in displayTimes"
        :key="sec"
        class="time-card page-card"
        :class="{
          active: selectedTimeSeconds === sec,
          locked: selectedCrop != null && selectedTimeSeconds !== sec,
        }"
        @click="selectTime(sec)"
      >
        <div class="time-value">{{ formatDuration(sec) }}</div>
        <div class="time-crop-count">{{ timeCropCounts.get(sec) }} 种作物</div>
      </div>
    </div>

    <!-- 结果卡片区 -->
    <template v-if="baseSeconds != null">
      <div class="section-header">
        <span class="section-title-text">收菜时间预估</span>
        <el-button
          class="info-btn"
          :icon="InfoFilled"
          text
          size="small"
          @click="showMechanism = true"
        >
          计算原理
        </el-button>
        <div class="strategy-switcher">
          <el-radio-group v-model="selectedStrategy" size="small">
            <el-radio-button
              v-for="opt in strategyOptions"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </div>
      </div>
      
      <div class="result-transition-wrapper">
        <transition name="result-fade" mode="out-in">
          <div v-if="filteredResults.length === 0" key="empty" class="empty-tip">
            暂无数据
          </div>
          
          <div v-else :key="selectedStrategy" class="result-grid">
            <div v-for="item in filteredResults" :key="item.strategy" class="result-card page-card">
              <h3>{{ item.label }}</h3>
              <p class="time">{{ item.formatted }}</p>
              <p class="desc">{{ item.description }}</p>
              <p v-if="item.matureAt" class="mature-at">收菜时间：{{ item.matureAt }}</p>
              <!-- 浇水/收菜节点列表 -->
              <div v-if="item.nodes.length > 0" class="r-nodelist">
                <div
                  v-for="node in item.nodes"
                  :key="node.index"
                  class="r-node-item"
                  :class="{ 'is-harvest': node.harvest }"
                >
                  <span class="r-node-num">{{ node.index }}</span>
                  <div class="r-node-body">
                    <div class="r-node-title">{{ node.title }}</div>
                    <div class="r-node-desc">{{ node.desc }}</div>
                  </div>
                  <div v-if="node.timeStr && !(node.index === 1 && !node.harvest)" class="r-node-time">
                    <el-icon><AlarmClock /></el-icon>
                    {{ node.timeStr }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </transition>
      </div>
    </template>

    <!-- 计算原理弹窗 -->
    <el-dialog v-model="showMechanism" title="浇水机制" width="620px" align-center>
      <div class="mechanism-body">
        <p class="m-intro">设作物标准收获时间为 <strong>T</strong>，浇水遵循以下规则：</p>
        <div class="m-item">
          <span class="m-num">1</span>
          <div class="m-content">
            <h4>满水维持时间</h4>
            <p>刚种下时水分维持度为 0（干涸状态）。浇水后进入满水，持续 <strong>W = T / 3</strong> 的时间。之后逐渐蒸发，直到再次干涸。</p>
          </div>
        </div>
        <div class="m-item">
          <span class="m-num">2</span>
          <div class="m-content">
            <h4>浇水减时</h4>
            <p>每次浇水减少的收获时间 = <strong>已蒸发水量 / 4</strong>。即：消耗的水分越多，浇水带来的减时效果越大。浇水后水分立刻恢复满值。</p>
          </div>
        </div>
        <div class="m-item">
          <span class="m-num">3</span>
          <div class="m-content">
            <h4>浇水门槛</h4>
            <p>只有当水分消耗 ≥ <strong>T / 30</strong> 时才能浇水，刚浇完不能立刻再浇。</p>
          </div>
        </div>
        <div class="m-item">
          <span class="m-num">4</span>
          <div class="m-content">
            <h4>自然成熟</h4>
            <p>完全不浇水，等待作物自然成熟，耗时等于标准收获时间。</p>
          </div>
        </div>
        <div class="m-item">
          <span class="m-num">5</span>
          <div class="m-content">
            <h4>佛系浇水</h4>
            <p>刚种下浇1次水，再等待 5T/6 后浇1次水直接成熟，缩短约16.7%的收获时间。</p>
          </div>
        </div>
        <div class="m-item">
          <span class="m-num">6</span>
          <div class="m-content">
            <h4>勤奋浇水</h4>
            <p>刚种下浇1次水，等干涸后浇1次水，再等干涸后浇1次水，共浇3次水，缩短25%的收获时间。</p>
          </div>
        </div>
        <div class="m-item">
          <span class="m-num">7</span>
          <div class="m-content">
            <h4>极限浇水</h4>
            <p>刚种下浇1次水，等干涸后浇2次水，再等待 T/15 后浇1次水直接成熟，共浇4次水，缩短约26.7%的收获时间。</p>
          </div>
        </div>
        <div class="m-note">以上策略已内置到计算器中，选择成熟时间后自动给出 4 种浇水方案的结果。</div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.harvest-calc {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.form-section {
  margin-bottom: 14px;
  padding: 16px 18px;
}

.page-title {
  font-size: 20px;
  margin: 0 0 6px 0;
  font-weight: 700;
  color: var(--fh-green);
}

.page-desc {
  font-size: 13px;
  color: var(--fh-muted);
  margin: 0 0 14px 0;
  line-height: 1.5;
}

/* 计算按钮 */
.calc-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 24px;
  background: #e67e22;
  border: none;
  border-radius: 6px;
  color: white;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.calc-button:hover {
  background: #d35400;
  transform: translateY(-1px);
}

.calc-button:active {
  transform: translateY(0);
}

.calc-button:disabled {
  background: #f0b27a;
  cursor: not-allowed;
  transform: none;
}

/* 计算模式单选按钮组 - 紫色系（只针对计算模式这一行） */
.mode-radio-group :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background-color: #9b59b6;
  border-color: #9b59b6;
  color: white;
  box-shadow: none;
}

.mode-radio-group :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner:hover) {
  background-color: #8e44ad;
  border-color: #8e44ad;
}

.mode-radio-group :deep(.el-radio-button__inner:hover) {
  color: #8e44ad;
}

/* 输入方式单选按钮组 - 保持Element Plus默认蓝色系 */

.button-text {
  line-height: 1;
}

/* 分组标题行 */
.section-title {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 700;
  color: var(--fh-green);
  margin-bottom: 8px;
  line-height: 1;
  flex-wrap: wrap;
}

.section-sub {
  font-size: 11px;
  font-weight: 400;
  color: var(--fh-muted);
}

/* 结果区域头部 */
.section-header {
  margin-top: 16px;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-title-text {
  font-size: 13px;
  font-weight: 700;
  color: var(--fh-green);
  white-space: nowrap;
  line-height: 1;
  display: inline-flex;
  align-items: center;
}

/* 计算原理按钮 - 美化样式 */
.info-btn {
  font-size: 12px;
  color: #409eff;
  padding: 4px 10px;
  margin-left: 6px;
  vertical-align: middle;
  line-height: 1;
  background: #ecf5ff;
  border: 1.5px solid #b3d8ff;
  border-radius: 4px;
  transition: all 0.2s;
}

.info-btn:hover {
  color: #1d6bc0;
  background: #c9e5ff !important;
  border-color: #66b1ff;
}

.info-btn :deep(.el-icon) {
  font-size: 13px;
  vertical-align: middle;
  margin-right: 2px;
}

/* 策略切换器 */
.strategy-switcher {
  margin-left: auto;
  display: flex;
  align-items: center;
}

.strategy-switcher :deep(.el-radio-group) {
  display: inline-flex;
}

.strategy-switcher :deep(.el-radio-button__inner) {
  padding: 3px 10px;
  font-size: 12px;
  line-height: 1.3;
  font-weight: 500;
}

.mode-switch {
  margin-left: 4px;
  display: inline-flex;
  align-items: center;
}

/* el-radio-button 整体对齐 */
.section-title :deep(.el-radio-group) {
  display: inline-flex;
  vertical-align: middle;
}

.section-title :deep(.el-radio-button__inner) {
  padding: 3px 10px;
  font-size: 12px;
  line-height: 1.3;
  font-weight: 500;
}

/* 成熟时间卡片区域的切换按钮*/
.time-mode-group :deep(.el-radio-button__inner) {
  padding: 3px 8px;
  font-size: 11px;
  line-height: 1.2;
  font-weight: 500;
}

.selected-badge {
  margin-left: auto;
  background: var(--fh-green);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  padding: 3px 12px;
  border-radius: 12px;
  line-height: 1;
}

.locked-badge {
  margin-left: auto;
  background: #f0c060;
  color: #5a3e00;
  font-size: 13px;
  font-weight: 500;
  padding: 3px 12px;
  border-radius: 12px;
  line-height: 1;
}

/* 时间卡片网格 */
.time-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: 8px;
  margin-bottom: 14px;
}

.time-card {
  text-align: center;
  padding: 12px 8px;
  cursor: pointer;
  transition: all 0.2s;
  border: 2px solid transparent;
}

.time-card:hover:not(.locked) {
  border-color: var(--fh-green);
  transform: translateY(-2px);
}

.time-card.active {
  border-color: var(--fh-green);
  background: linear-gradient(135deg, #c8e6c9, #dcedd8);
  box-shadow: 0 2px 12px rgba(76, 153, 89, 0.18);
}

.time-card.locked {
  opacity: 0.4;
  cursor: not-allowed;
  filter: grayscale(30%);
}

.time-value {
  font-size: 16px;
  font-weight: 700;
  color: #2c3e2e;
  margin-bottom: 2px;
}

.time-crop-count {
  font-size: 11px;
  color: var(--fh-muted);
}

.time-card.active .time-value {
  color: var(--fh-green);
}

.loading-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--fh-muted);
  font-size: 14px;
  padding: 20px 0;
  line-height: 1;
}

.loading-tip .el-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  vertical-align: middle;
}

.empty-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--fh-muted);
  font-size: 14px;
  padding: 40px 0;
  background: #fafbfc;
  border-radius: 8px;
  border: 2px dashed #e0e6ea;
}

/* 计算原理按钮 */
.mechanism-btn {
  margin-left: auto;
  font-size: 14px;
  color: #909399;
  padding: 4px 8px;
}

/* 弹窗内容 */
.mechanism-body {
  padding: 4px 0;
}

.m-intro {
  margin: 0 0 20px;
  font-size: 14px;
  color: #606266;
  line-height: 1.7;
}

.m-item {
  display: flex;
  gap: 14px;
  margin-bottom: 18px;
}

.m-item:last-child {
  margin-bottom: 0;
}

.m-num {
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  line-height: 26px;
  text-align: center;
  background: var(--fh-green);
  color: #fff;
  border-radius: 50%;
  font-size: 14px;
  font-weight: 700;
  margin-top: 0;
}

.m-content h4 {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 700;
  color: var(--fh-green);
}

.m-content p {
  margin: 0;
  font-size: 14px;
  color: #606266;
  line-height: 1.7;
}

.m-note {
  margin-top: 24px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}

/* 结果卡片：单列，每个策略占一行 */
.result-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}

/* 动画容器 */
.result-transition-wrapper {
  position: relative;
  overflow: hidden;
}

/* 切换时间时的过渡动画 */
.result-fade-enter-active {
  transition: opacity 0.3s ease;
}

.result-fade-leave-active {
  transition: opacity 0.25s ease;
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
}

.result-fade-enter-from {
  opacity: 0;
}

.result-fade-leave-to {
  opacity: 0;
}

.result-card h3 {
  margin: 0 0 6px;
  font-size: 15px;
  color: var(--fh-green);
}

.time {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 700;
}

.desc {
  margin: 0;
  font-size: 12px;
  color: var(--fh-muted);
}

.mature-at {
  margin: 8px 0 0;
  font-size: 14px;
  color: var(--fh-green-light);
  font-weight: 700;
}

/* 结果卡片内节点纵向列表，每个节点占一行 */
.r-nodelist {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.r-node-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  padding: 3px 0;
  width: 650px;
  max-width: 100%;
}

/* 去掉悬停变色 */
.r-node-item:hover {
  background: transparent;
}

.r-node-item.is-harvest {
  background: transparent;
}

.r-node-item.is-harvest:hover {
  background: transparent;
}

.r-node-num {
  width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  background: var(--fh-green);
  color: #fff;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.r-node-item.is-harvest .r-node-num {
  background: #e6a23c;
}

.r-node-body {
  text-align: left;
  align-self: flex-start;
}

.r-node-title {
  font-size: 13px;
  font-weight: 700;
  color: #303133;
  line-height: 1.4;
  margin-bottom: 1px;
}

.r-node-desc {
  font-size: 11px;
  color: #909399;
  line-height: 1.4;
}

.r-node-time {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 15px;
  font-weight: 700;
  color: #e65100;
  white-space: nowrap;
  margin-left: auto;
  font-family: 'Liberation Mono', monospace;
}

.r-node-time :deep(.el-icon) {
  display: inline-flex;
  align-items: center;
}
</style>
