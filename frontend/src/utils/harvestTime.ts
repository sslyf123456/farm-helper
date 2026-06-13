/**
 * 将收获时间格式化为中文可读形式。
 * 接受 number（新 API）或字符串（兼容），返回中文描述。
 */
export function formatHarvestTime(value: string | number | null | undefined): string {
  const seconds = parseHarvestTime(value)
  if (seconds === null) return '--'
  return formatDuration(seconds)
}

/** 将收获时间解析为秒数 */
export function parseHarvestTime(value: string | number | null | undefined): number | null {
  if (value === null || value === undefined) return null
  if (typeof value === 'number') return Number.isFinite(value) && value >= 0 ? value : null
  const n = Number(value)
  return Number.isFinite(n) && n >= 0 ? n : null
}

/** 将收获时间解析为小时数 */
export function parseHarvestHours(value: string | number | null | undefined): number {
  const seconds = parseHarvestTime(value)
  return seconds ? seconds / 3600 : 0
}

/** 秒数格式化为可读中文 */
export function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds} 秒`

  const parts: string[] = []
  let remain = Math.round(seconds)

  const hours = Math.floor(remain / 3600)
  if (hours > 0) {
    parts.push(`${hours} 小时`)
    remain %= 3600
  }

  const minutes = Math.floor(remain / 60)
  if (minutes > 0) {
    parts.push(`${minutes} 分钟`)
    remain %= 60
  }

  if (remain > 0 && hours === 0) {
    parts.push(`${remain} 秒`)
  }

  return parts.join('') || '0 秒'
}

export type WaterStrategy = 'none' | 'once' | 'diligent' | 'extreme'

export const WATER_STRATEGIES: {
  key: WaterStrategy
  label: string
  desc: string
}[] = [
  { key: 'none', label: '完全不浇水', desc: '自然成熟，耗时 T' },
  { key: 'once', label: '种下时浇一次', desc: '减少 T/12，耗时 11T/12' },
  { key: 'diligent', label: '勤奋浇水', desc: '种下 1 次 + 干涸后 2 次，耗时 3T/4' },
  { key: 'extreme', label: '极限浇水', desc: '勤奋基础上再等 T/15 浇一次，耗时 11T/15' },
]

/** 根据策略计算实际成熟秒数 */
export function calcMaturitySeconds(baseSeconds: number, strategy: WaterStrategy): number {
  const T = baseSeconds
  switch (strategy) {
    case 'none':
      return T
    case 'once':
      return (11 * T) / 12
    case 'diligent':
      return (3 * T) / 4
    case 'extreme':
      return (11 * T) / 15
    default:
      return T
  }
}
