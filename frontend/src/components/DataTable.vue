<script setup lang="ts">

import { computed, onMounted, onUnmounted, ref } from 'vue'

import type { CsvRow } from '@/api/static'



const props = withDefaults(defineProps<{

  rows: CsvRow[]

  loading?: boolean

  searchKeys?: string[]

  searchPlaceholder?: string

  columnLabels?: Record<string, string>

  columnOrder?: string[]

  hideSearch?: boolean

  numericColumns?: string[]

  noSortColumns?: string[]

  columnWidthPct?: Record<string, number>  // 百分比，如 { unlock_content: 60 }

  tableMinWidth?: number  // 表格总宽最小值（px），未设则按列数 × 100

}>(), {

  searchKeys: () => [],

  searchPlaceholder: '输入关键词搜索...',

  columnLabels: () => ({}),

  columnOrder: () => [],

  hideSearch: false,

  numericColumns: () => [],

  noSortColumns: () => [],

  columnWidthPct: () => ({}),

  tableMinWidth: undefined,

})



const keyword = ref('')
const wrapRef = ref<HTMLElement>()
const viewportWidth = ref(0)

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  const el = wrapRef.value
  if (!el) return
  viewportWidth.value = el.clientWidth
  resizeObserver = new ResizeObserver(([entry]) => {
    viewportWidth.value = entry.contentRect.width
  })
  resizeObserver.observe(el)
})

onUnmounted(() => {
  resizeObserver?.disconnect()
})

const columns = computed(() => {

  let keys: string[]

  if (props.rows.length) {

    keys = Object.keys(props.rows[0])

  } else if (props.columnOrder.length) {

    keys = [...props.columnOrder]

  } else if (Object.keys(props.columnLabels).length) {

    keys = Object.keys(props.columnLabels)

  } else {

    return []

  }

  if (!props.columnOrder.length) return keys

  const ordered = props.columnOrder.filter(k => keys.includes(k))

  const rest = keys.filter(k => !ordered.includes(k))

  return [...ordered, ...rest]

})



const tableKey = computed(() => columns.value.join('\0'))



const sortCol = ref<string | null>(null)

const sortOrder = ref<'ascending' | 'descending' | null>(null)



const filteredRows = computed(() => {

  const kw = keyword.value.trim().toLowerCase()

  if (!kw) return props.rows



  const keys = props.searchKeys?.length ? props.searchKeys : columns.value

  return props.rows.filter((row) =>

    keys.some((key) => {
      const val = row[key]
      if (val === null || val === undefined) return false
      return String(val).toLowerCase().includes(kw)
    }),

  )

})



const displayRows = computed(() => {

  const col = sortCol.value

  const order = sortOrder.value

  if (!col || !order) return filteredRows.value



  const arr = [...filteredRows.value]

  const asc = order === 'ascending'



  arr.sort((a, b) => {

    const cmp = compareCol(a, b, col)

    return asc ? cmp : -cmp

  })



  return arr

})



function compareCol(a: CsvRow, b: CsvRow, col: string): number {

  const av = a[col]

  const bv = b[col]

  const aEmpty = isEmpty(av)

  const bEmpty = isEmpty(bv)

  if (aEmpty && bEmpty) return 0

  if (aEmpty) return 1

  if (bEmpty) return -1



  if (props.numericColumns.includes(col)) {

    const va = typeof av === 'number' ? av : parseChineseNumber(String(av))

    const vb = typeof bv === 'number' ? bv : parseChineseNumber(String(bv))

    return va - vb

  }



  return String(av).localeCompare(String(bv), 'zh-CN')

}



function colLabel(col: string): string {

  return props.columnLabels[col] ?? col

}



function isSortable(col: string): boolean {

  return !props.noSortColumns.includes(col)

}



// 估算表头所需像素宽度（中文约 14px/字 + 排序图标 + 内边距）
function headerMinWidth(col: string): number {
  const label = colLabel(col)
  const sortable = isSortable(col)
  let w = label.length * 14 + (sortable ? 24 : 0) + 20
  if (col === 'name' || col === 'mutation_hero' || col === 'unlock_content') {
    w += 24
  }
  return w
}

// 列宽分配权重：有百分比用百分比，否则用表头像素宽度
function colWeight(col: string): number {
  const pct = props.columnWidthPct[col]
  if (pct) return pct
  return headerMinWidth(col)
}

const effectiveTableMinWidth = computed(() => {
  const cols = columns.value
  if (!cols.length) return 0
  const headerSum = cols.reduce((sum, col) => sum + headerMinWidth(col), 0)
  const custom = props.tableMinWidth ?? 0
  return Math.max(custom, headerSum)
})

// 视口窄于阈值：固定表格总宽，由浏览器底部横向滚动；否则按容器宽度分配
const isCompact = computed(() => {
  const min = effectiveTableMinWidth.value
  return min > 0 && viewportWidth.value > 0 && viewportWidth.value < min
})

const allocWidth = computed(() => {
  const min = effectiveTableMinWidth.value
  const vw = viewportWidth.value
  if (!min) return 0
  if (vw > 0 && vw >= min) return vw
  return min
})

const tableFit = computed(() => false)

const tableStyle = computed(() => {
  const min = effectiveTableMinWidth.value
  const w = allocWidth.value
  if (!min) return undefined
  if (isCompact.value) {
    return { width: `${w}px`, minWidth: `${w}px` }
  }
  return { width: '100%', minWidth: `${min}px` }
})

// 按权重分配列宽，且每列不低于表头所需宽度
const columnMinWidths = computed((): Record<string, number> => {
  const cols = columns.value
  const total = allocWidth.value
  if (!cols.length || total <= 0) return {}

  const mins = cols.map(col => headerMinWidth(col))
  const minSum = mins.reduce((a, b) => a + b, 0)
  const weights = cols.map(col => colWeight(col))
  const weightSum = weights.reduce((a, b) => a + b, 0) || cols.length

  const result: Record<string, number> = {}
  cols.forEach((col, i) => { result[col] = mins[i] })

  let remaining = total - minSum
  if (remaining <= 0) return result

  let allocated = 0
  cols.forEach((col, i) => {
    if (i === cols.length - 1) {
      result[col] += remaining - allocated
    } else {
      const extra = Math.floor(remaining * weights[i] / weightSum)
      result[col] += extra
      allocated += extra
    }
  })
  return result
})



function colMinWidth(col: string): number {
  return columnMinWidths.value[col] ?? headerMinWidth(col)
}



function isEmpty(val: unknown): boolean {

  return val === '' || val === undefined || val === null

}



function parseChineseNumber(val: string): number {

  const s = String(val).trim().replace(/,/g, '')

  if (s === '') return NaN

  let num: number

  if (s.endsWith('亿')) {

    num = parseFloat(s) * 1_0000_0000

  } else if (s.endsWith('万')) {

    num = parseFloat(s) * 1_0000

  } else if (s.endsWith('%')) {

    num = parseFloat(s)

  } else {

    num = parseFloat(s)

  }

  return num

}



function onSortChange(params: { column: { property?: string }; prop: string; order: 'ascending' | 'descending' | null }) {

  sortCol.value = params.prop || params.column?.property || null

  sortOrder.value = params.order

}

</script>



<template>

  <div ref="wrapRef" class="fh-table-wrap">

    <div v-if="!hideSearch" class="fh-toolbar">

      <el-input

        v-model="keyword"

        :placeholder="searchPlaceholder"

        clearable

        class="fh-search-input"

      />

      <span class="fh-count">共 {{ filteredRows.length }} 条</span>

    </div>

    <el-table

      :key="tableKey"

      v-loading="loading"

      :data="displayRows"

      :fit="tableFit"

      stripe

      border

      empty-text="暂无数据"

      class="fh-el-table"

      :style="tableStyle"

      @sort-change="onSortChange"

    >

      <el-table-column

        v-for="col in columns"

        :key="col"

        :prop="col"

        :label="colLabel(col)"

        :width="colMinWidth(col)"

        show-overflow-tooltip

        :sortable="isSortable(col) ? 'custom' : false"

      />

    </el-table>

  </div>

</template>



<style scoped>

.fh-table-wrap {
  position: relative;
  width: max-content;
  min-width: 100%;
}
.fh-toolbar {

  display: flex;

  align-items: center;

  justify-content: space-between;

  margin-bottom: 16px;

  gap: 12px;

}

.fh-search-input {

  max-width: 280px;

}

.fh-count {

  color: var(--fh-muted, #909399);

  font-size: 13px;

}

</style>



<style>
.fh-table-wrap .fh-el-table.el-table {
  max-width: none;
}
/* 禁止 el-table 内部横向滚动，窄视口时由浏览器底部统一滚动 */
.fh-table-wrap .fh-el-table .el-table__header-wrapper,
.fh-table-wrap .fh-el-table .el-table__body-wrapper,
.fh-table-wrap .fh-el-table .el-table__footer-wrapper {
  overflow-x: hidden !important;
}
.fh-table-wrap .fh-el-table .el-scrollbar__bar.is-horizontal {
  display: none !important;
}
.fh-el-table .el-table__header th.el-table__cell .cell {
  white-space: nowrap !important;
}
</style>

