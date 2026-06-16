<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { InfoFilled, AlarmClock } from '@element-plus/icons-vue'
import { Loading } from '@element-plus/icons-vue'
import { fetchCrops, type CsvRow } from '@/api/static'
import {
  WATER_STRATEGIES,
  calcMaturitySeconds,
  formatDuration,
  formatHarvestTime,
  parseHarvestTime,
  type WaterStrategy,
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

onMounted(async () => {
  plantTime.value = new Date()
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

/** 最终用于计算的基础秒数 */
const baseSeconds = computed(() => selectedTimeSeconds.value)

/** 选择时间卡片 */
function selectTime(seconds: number) {
  if (selectedCrop.value) return
  selectedTimeSeconds.value = seconds
}

/** 根据策略类型，计算各浇水时刻（相对于种下时间的秒数） */
function getWaterTimes(baseSeconds: number, key: WaterStrategy): number[] {
  const T = baseSeconds
  const W = T / 3          // 满水维持时间
  const wait = T / 15       // 极限策略最后一次等待时间
  switch (key) {
    case 'none':
      return []               // 不浇水
    case 'once':
      // 佛系：种下浇1次，再等 5T/6 后浇1次直接成熟
      return [0, (5 * T) / 6]
    case 'diligent':
      // 种下 0、等W后、再等W后
      return [0, W, 2 * W]
    case 'extreme':
      // 种下 0、等W后、再等W后、再等wait后
      return [0, W, 2 * W, 2 * W + wait]
    default:
      return []
  }
}

/** 节点类型 */
interface TimeNode {
  /** 步骤编号（从1开始） */
  index: number
  /** 标题文字 */
  title: string
  /** 描述：减时 + 剩余 + 湿润 */
  desc: string
  /** 该时刻距种下的秒数 */
  offsetSeconds: number
  /** 具体时间字符串（如 "今天 03:05"） */
  timeStr: string | null
  /** 是否为最终收菜节点 */
  isHarvest: boolean
}

/** 格式化时间为 "YYYY-MM-DD HH:mm"（完整日期） */
function formatTimeFull(base: Date, offsetSec: number): string {
  const d = new Date(base.getTime() + offsetSec * 1000)
  const yyyy = String(d.getFullYear())
  const mo = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${yyyy}-${mo}-${dd} ${hh}:${mm}`
}

/** 根据策略生成浇水/收菜节点列表 */
function buildTimeNodes(
  baseSec: number,
  key: WaterStrategy,
  matureSeconds: number,
  plantDate: Date,
): TimeNode[] {
  const T = baseSec
  const W = T / 3
  const waterTimes = getWaterTimes(baseSec, key)
  const nodes: TimeNode[] = []

  // extreme 和 once 策略：最后一个浇水点与成熟同时发生，合并展示，循环不含最后一点
  const loopEnd = (key === 'extreme' || key === 'once') ? waterTimes.length - 1 : waterTimes.length

  let remaining = T // 剩余成熟时间，逐次减去等待时间与浇水减时

  for (let i = 0; i < loopEnd; i++) {
    const wt = waterTimes[i]
    const prevTime = i > 0 ? waterTimes[i - 1] : 0
    const gap = wt - prevTime
    // 种下立即浇水：土地干涸(w=0)，消耗=W，减时=T/12
    // 之后浇水：上次浇水后满水(w=W)，经过gap时间线性蒸发，消耗=min(gap,W)，减时=消耗/4
    const reduction = i === 0 ? calcFullReduction(T) : calcWaterReduction(T, gap)
    remaining = remaining - gap - reduction
    if (i > 0) {
      nodes.push({
        index: nodes.length + 1,
        title: `等${formatDuration(gap)}后浇水`,
        desc: `浇水减${formatDuration(reduction)}，剩余${formatDuration(remaining)}，水分可以维持${formatDuration(W)}`,
        offsetSeconds: wt,
        timeStr: formatTimeFull(plantDate, wt),
        isHarvest: false,
      })
    } else {
      nodes.push({
        index: 1,
        title: '种下立即浇水',
        desc: `浇水减${formatDuration(reduction)}，剩余${formatDuration(remaining)}，水分可以维持${formatDuration(W)}`,
        offsetSeconds: 0,
        timeStr: null,
        isHarvest: false,
      })
    }
  }

  // 最终收菜节点
  const finalGap = matureSeconds - (waterTimes.length > 0 ? waterTimes[waterTimes.length - 1] : 0)
  let harvestTitle = '直接成熟'
  let harvestDesc = ''
  if (key === 'none') {
    harvestTitle = '自然成熟'
    harvestDesc = '不浇水，等待自然成熟'
  } else if (key === 'extreme') {
    // 极限：最后一步是浇水即熟，合并展示
    const waitSec = waterTimes[waterTimes.length - 1] - waterTimes[waterTimes.length - 2]
    const reduction = calcWaterReduction(T, waitSec)
    harvestTitle = `等${formatDuration(waitSec)}后浇水秒熟`
    harvestDesc = `浇水减${formatDuration(reduction)}，直接成熟`
  } else if (key === 'once') {
    // 佛系：水分先蒸发完，再等一段时间后浇水直接成熟
    const waitSec = waterTimes[waterTimes.length - 1] - waterTimes[waterTimes.length - 2]
    const dryWait = Math.max(0, waitSec - W) // 蒸发完后额外等待的时间
    const reduction = calcWaterReduction(T, waitSec)
    harvestTitle = `等${formatDuration(waitSec)}后浇水秒熟`
    if (dryWait > 60) {
      harvestDesc = `水分在${formatDuration(W)}后蒸发完，再等${formatDuration(dryWait)}，浇水减${formatDuration(reduction)}直接成熟`
    } else {
      harvestDesc = `浇水减${formatDuration(reduction)}，直接成熟`
    }
  } else if (key === 'diligent') {
    // 勤奋：最后等剩余时间自然成熟
    harvestTitle = `再等${formatDuration(remaining)}后自然成熟`
    harvestDesc = '不需要浇水，直接自然成熟'
  } else if (finalGap > 60) {
    const reduction = calcWaterReduction(T, finalGap)
    if (reduction > 0) {
      harvestTitle = `再等${formatDuration(finalGap)}后浇水秒熟`
      harvestDesc = `浇水减${formatDuration(reduction)}，直接成熟`
    } else {
      harvestTitle = `再等${formatDuration(finalGap)}后自然成熟`
      harvestDesc = '不需要浇水，直接自然成熟'
    }
  } else {
    harvestDesc = `减时${formatDuration(calcWaterReduction(T, finalGap))}，直接成熟`
  }
  nodes.push({
    index: nodes.length + 1,
    title: harvestTitle,
    desc: harvestDesc,
    offsetSeconds: matureSeconds,
    timeStr: formatTimeFull(plantDate, matureSeconds),
    isHarvest: true,
  })

  return nodes
}

/** 计算某次浇水的减时量
 * 水分从满值 W 线性蒸发，蒸发速率 = 1（每秒消耗 1 秒水分值），无湿润期。
 * 浇水减时 = 消耗水分 / 4 = min(gap, W) / 4
 * - 间隔 >= W（干涸后浇水）：消耗 = W，减时 = W/4 = T/12
 * - 间隔 < W（未干涸时浇水）：消耗 = gap，减时 = gap / 4
 */
function calcWaterReduction(T: number, gap: number): number {
  const W = T / 3
  const consumed = Math.min(gap, W) // 实际消耗水分
  return Math.round(consumed / 4)
}

/** 浇水减时（固定值）：每次在干涸后浇水，减时 = W/4 = T/12 */
function calcFullReduction(T: number): number {
  return Math.round(T / 12)
}

/** 策略结果 */
const results = computed(() => {
  if (baseSeconds.value == null) return []
  return WATER_STRATEGIES.map((s) => {
    const seconds = calcMaturitySeconds(baseSeconds.value!, s.key)
    const nodes = buildTimeNodes(baseSeconds.value!, s.key, seconds, plantTime.value)
    let matureAt: string | null = null
    if (plantTime.value) {
      const t = new Date(plantTime.value.getTime() + seconds * 1000)
      matureAt = t.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      })
    }
    return { ...s, seconds, formatted: formatDuration(seconds), nodes, matureAt }
  })
})
</script>

<template>
  <div class="harvest-calc">
    <!-- 顶部表单区 -->
    <div class="page-card form-section">
      <h1 class="page-title">收菜计算器</h1>
      <p class="page-desc">选择作物或直接点击成熟时间卡片，查看各浇水策略下的收菜时间</p>

      <el-form label-width="70px" label-position="left">
        <!-- 作物选择（可选） -->
        <el-form-item label="选择作物">
          <el-select
            v-model="selectedCropName"
            filterable
            clearable
            placeholder="可选，不选时下方卡片可自由切换"
            style="width: 280px"
          >
            <el-option
              v-for="c in filteredCrops"
              :key="String(c['name'])"
              :label="`Lv.${c['unlock_level']} ${c['name']}（${formatHarvestTime(c['harvest_time'])}）`"
              :value="String(c['name'])"
            />
          </el-select>
        </el-form-item>

        <!-- 种下时间 -->
        <el-form-item label="种下时间">
          <el-date-picker
            v-model="plantTime"
            type="datetime"
            placeholder="选择种下时间"
            format="YYYY-MM-DD HH:mm"
            style="width: 220px"
          />
        </el-form-item>
      </el-form>
    </div>

    <!-- 时间卡片区 -->
    <div class="section-title">
      <span>成熟时间</span>
      <span class="section-sub">（必选，点击卡片）</span>
      <div class="mode-switch">
        <el-radio-group v-model="timeMode" size="small">
          <el-radio-button value="common">常见</el-radio-button>
          <el-radio-button value="all">所有</el-radio-button>
        </el-radio-group>
      </div>
      <span v-if="selectedCropName" class="locked-badge">
        已锁定为 {{ selectedCropName }}
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
      <div class="section-title" style="margin-top: 28px">
        <span>收菜时间预估</span>
        <el-button
          class="mechanism-btn"
          :icon="InfoFilled"
          text
          @click="showMechanism = true"
        >
          计算原理
        </el-button>
      </div>
      <div class="result-grid">
        <div v-for="item in results" :key="item.key" class="result-card page-card">
          <h3>{{ item.label }}</h3>
          <p class="time">{{ item.formatted }}</p>
          <p class="desc">{{ item.desc }}</p>
          <p v-if="item.matureAt" class="mature-at">收菜时间：{{ item.matureAt }}</p>
          <!-- 浇水/收菜节点列表 -->
          <div v-if="item.nodes.length > 0" class="r-nodelist">
            <div
              v-for="node in item.nodes"
              :key="node.index"
              class="r-node-item"
              :class="{ 'is-harvest': node.isHarvest }"
            >
              <span class="r-node-num">{{ node.index }}</span>
              <div class="r-node-body">
                <div class="r-node-title">{{ node.title }}</div>
                <div class="r-node-desc">{{ node.desc }}</div>
              </div>
              <div v-if="node.timeStr" class="r-node-time">
                <el-icon><AlarmClock /></el-icon>
                {{ node.timeStr }}
              </div>
            </div>
          </div>
        </div>
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
  margin-bottom: 20px;
}

/* 分组标题行 */
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: var(--fh-green);
  margin-bottom: 12px;
  line-height: 1;
}

.section-sub {
  font-size: 13px;
  font-weight: 400;
  color: var(--fh-muted);
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
  padding: 4px 12px;
  font-size: 13px;
  line-height: 1.4;
}

.selected-badge {
  margin-left: auto;
  background: var(--fh-green);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  padding: 4px 14px;
  border-radius: 14px;
  line-height: 1;
}

.locked-badge {
  margin-left: auto;
  background: #f0c060;
  color: #5a3e00;
  font-size: 14px;
  font-weight: 500;
  padding: 4px 14px;
  border-radius: 14px;
  line-height: 1;
}

/* 时间卡片网格 */
.time-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.time-card {
  text-align: center;
  padding: 18px 12px;
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
  font-size: 20px;
  font-weight: 700;
  color: #2c3e2e;
  margin-bottom: 4px;
}

.time-crop-count {
  font-size: 12px;
  color: var(--fh-muted);
}

.time-card.active .time-value {
  color: var(--fh-green);
}

.loading-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--fh-muted);
  font-size: 14px;
  padding: 20px 0;
}

/* 计算原理按钮 */
.mechanism-btn {
  margin-left: auto;
  font-size: 14px;
  color: #909399;
}

.mechanism-btn:hover {
  color: var(--fh-green);
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
  gap: 16px;
  margin-bottom: 24px;
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
  font-size: 15px;
  color: var(--fh-green-light);
  font-weight: 700;
}

/* 结果卡片内节点纵向列表，每个节点占一行 */
.r-nodelist {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.r-node-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
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
  width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  background: var(--fh-green);
  color: #fff;
  border-radius: 50%;
  font-size: 12px;
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
  font-size: 14px;
  font-weight: 700;
  color: #303133;
  line-height: 1.4;
  margin-bottom: 1px;
}

.r-node-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.r-node-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 16px;
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
