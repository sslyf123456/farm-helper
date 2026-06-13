<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import DataTable from '@/components/DataTable.vue'
import type { CsvRow } from '@/api/static'

import { fetchFarmLevels, fetchStall, fetchLand, fetchCrops, fetchCultivation } from '@/api/static'
import { formatHarvestTime, parseHarvestHours } from '@/utils/harvestTime'

type TabKey = 'farm-levels' | 'stall' | 'land' | 'crops' | 'cultivation'

const tabMap: Record<TabKey, { label: string; fetcher: () => Promise<CsvRow[]>; props: Record<string, any> }> = {
  'farm-levels': {
    label: '农场升级表',
    fetcher: fetchFarmLevels,
    props: {
      hideSearch: true,
      numericColumns: ['level'],
      noSortColumns: ['upgrade_cost', 'required_exp', 'unlock_content'],
      columnWidthPct: { level: 10, upgrade_cost: 15, required_exp: 15, unlock_content: 60 },
      columnLabels: { level: '等级', upgrade_cost: '升级费用', required_exp: '需要经验', unlock_content: '解锁内容' },
    },
  },
  'stall': {
    label: '小摊表',
    fetcher: fetchStall,
    props: {
      hideSearch: true,
      numericColumns: ['level', 'required_farm_level'],
      noSortColumns: ['upgrade_cost', 'gain_exp', 'required_farm_level', 'price_boost'],
      columnLabels: { level: '等级', upgrade_cost: '升级费用', gain_exp: '获得经验', required_farm_level: '需要等级', price_boost: '提升售价' },
    },
  },
  'land': {
    label: '土地表',
    fetcher: fetchLand,
    props: {
      hideSearch: true,
      numericColumns: [],
      noSortColumns: ['land_index', 'reclaim_cost', 'gain_exp', 'required_level'],
      columnLabels: { land_index: '农田', reclaim_cost: '开垦费用', gain_exp: '获得经验', required_level: '需要等级' },
    },
  },
  'crops': {
    label: '农作物数据表',
    fetcher: fetchCrops,
    props: {
      hideSearch: false,
      searchKeys: ['name', 'mutation_hero'],
      searchPlaceholder: '按作物名、变异英雄搜索...',
      columnOrder: ['unlock_level', 'name', 'seed_price', 'yield_qty', 'total_sell_price', 'sell_per_hour', 'exp_gain', 'exp_per_hour', 'harvest_time', 'mutation_limit', 'mutation_hero'],
      numericColumns: ['unlock_level', 'seed_price', 'yield_qty', 'total_sell_price', 'sell_per_hour', 'exp_gain', 'exp_per_hour'],
      noSortColumns: ['name', 'harvest_time', 'mutation_limit', 'mutation_hero'],
      columnLabels: { unlock_level: '等级', name: '作物', seed_price: '购买价格', yield_qty: '产量', total_sell_price: '总售价', sell_per_hour: '每小时售价', exp_gain: '经验', exp_per_hour: '每小时经验', harvest_time: '收获时间', mutation_limit: '变异上限', mutation_hero: '变异英雄' },
    },
  },
  'cultivation': {
    label: '农作物培育度表',
    fetcher: fetchCultivation,
    props: {
      hideSearch: false,
      searchKeys: ['crop'],
      searchPlaceholder: '按作物名搜索...',
      numericColumns: ['total', 'lv2', 'lv3', 'lv4', 'lv5', 'lv6', 'lv7', 'lv8', 'lv9', 'lv10'],
      noSortColumns: ['crop', 'lv2', 'lv3', 'lv4', 'lv5', 'lv6', 'lv7', 'lv8', 'lv9', 'lv10'],
      columnLabels: { crop: '作物', total: '合计', lv2: '2级', lv3: '3级', lv4: '4级', lv5: '5级', lv6: '6级', lv7: '7级', lv8: '8级', lv9: '9级', lv10: '10级' },
    },
  },
}

const activeTab = ref<TabKey>('farm-levels')
const rows = ref<CsvRow[]>([])
const loading = ref(false)

async function loadData(tab: TabKey) {
  loading.value = true
  rows.value = []
  try {
    const raw = await tabMap[tab].fetcher()
    if (tab === 'crops') {
      rows.value = raw.map(row => {
        const hours = parseHarvestHours(row['harvest_time'])
        const sellPrice = Number(row['total_sell_price'] ?? 0)
        const expGain = Number(row['exp_gain'] ?? 0)
        const sellPerHour = hours > 0 ? sellPrice / hours : 0
        const expPerHour = hours > 0 ? expGain / hours : 0
        return {
          ...row,
          harvest_time: formatHarvestTime(row['harvest_time']),
          sell_per_hour: sellPerHour.toFixed(2),
          exp_per_hour: expPerHour.toFixed(2),
        }
      })
    } else {
      rows.value = raw
    }
  } catch {
    ElMessage.error('数据加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 初始加载
loadData(activeTab.value)

function onTabChange(tab: TabKey) {
  activeTab.value = tab
  loadData(tab)
}
</script>

<template>
  <div class="data-view">
    <div class="page-card data-page-card">
      <h1 class="page-title">农场数据</h1>
      <p class="page-desc">查看农场各类数值数据表，点击标签页切换</p>
      <el-tabs :model-value="activeTab" @tab-change="(val: any) => onTabChange(val as TabKey)" class="data-tabs">
        <el-tab-pane
          v-for="(item, key) in tabMap"
          :key="key"
          :label="item.label"
          :name="key"
        />
      </el-tabs>
      <DataTable
        :rows="rows"
        :loading="loading"
        :hide-search="tabMap[activeTab].props.hideSearch"
        :search-keys="tabMap[activeTab].props.searchKeys || []"
        :search-placeholder="tabMap[activeTab].props.searchPlaceholder || '输入关键词搜索...'"
        :column-order="tabMap[activeTab].props.columnOrder || []"
        :numeric-columns="tabMap[activeTab].props.numericColumns"
        :no-sort-columns="tabMap[activeTab].props.noSortColumns || []"
        :column-labels="tabMap[activeTab].props.columnLabels"
        :column-width-pct="tabMap[activeTab].props.columnWidthPct || {}"
      />
    </div>
  </div>
</template>

<style scoped>
.data-view {
  width: max-content;
  min-width: 100%;
  padding-right: 20px;
  box-sizing: content-box;
}
.data-page-card {
  width: 100%;
  min-width: max-content;
  max-width: none;
}
.data-tabs {
  margin-bottom: 16px;
}

:deep(.el-tabs__header) {
  margin-bottom: 16px;
}
</style>
