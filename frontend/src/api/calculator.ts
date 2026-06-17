import apiClient from './client'

/**
 * 浇水策略类型
 */
export type WaterStrategy = 'none' | 'once' | 'diligent' | 'extreme'

/**
 * 时间节点（浇水或收菜）
 */
export interface TimeNode {
  /** 步骤编号（从1开始） */
  index: number
  /** 标题文字 */
  title: string
  /** 描述：减时 + 剩余 + 湿润 */
  desc: string
  /** 该时刻距种下的秒数 */
  offsetSeconds: number
  /** 具体时间字符串（ISO-8601格式，可选） */
  timeStr: string | null
  /** 是否为最终收菜节点 */
  harvest: boolean
}

/**
 * 浇水计算响应
 */
export interface WateringResponse {
  /** 策略标识 */
  strategy: WaterStrategy
  /** 策略标签 */
  label: string
  /** 策略描述 */
  description: string
  /** 实际成熟秒数 */
  matureSeconds: number
  /** 格式化的成熟时间 */
  formatted: string
  /** 成熟时刻（格式化字符串，可选） */
  matureAt?: string
  /** 节点列表（浇水和收菜节点） */
  nodes: TimeNode[]
}

/**
 * 浇水计算请求
 */
export interface WateringRequest {
  /** 作物基础成熟时间（秒） */
  baseSeconds: number
  /** 浇水策略（仅用于单个策略计算） */
  strategy?: WaterStrategy
  /** 种植时间（ISO-8601格式字符串，可选） */
  plantTime?: string
  /** 计算模式：forward（正向）或 reverse（反向） */
  mode?: 'forward' | 'reverse'
  /** 剩余成熟时间（秒，仅用于 reverse 模式） */
  remainingSeconds?: number
  /** 成熟时间点（ISO-8601格式字符串，仅用于 reverse 模式） */
  matureTime?: string
  /** 当前时间（ISO-8601格式字符串，仅用于 reverse 模式） */
  currentTime?: string
  /** 水分维持时间（秒，表示当前水分还能维持多久，仅用于 reverse 模式） */
  moistureSeconds?: number
}

/**
 * 将 Date 转换为本地时间的 ISO-8601 格式字符串（不带时区）
 * 例如：2026-06-16T11:11:00
 */
function formatLocalDateTime(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`
}

/**
 * 计算所有浇水策略的结果（正向模式）
 */
export async function calculateAllStrategies(
  baseSeconds: number,
  plantTime?: Date,
): Promise<WateringResponse[]> {
  const request: WateringRequest = {
    baseSeconds,
    mode: 'forward',
    plantTime: plantTime ? formatLocalDateTime(plantTime) : undefined,
  }

  const response = await apiClient.post<WateringResponse[]>('/calculator/watering', request)
  return response.data
}

/**
 * 计算所有浇水策略的结果（反向模式 - 使用剩余成熟时间）
 */
export async function calculateAllStrategiesReverse(
  baseSeconds: number,
  remainingSeconds: number,
  moistureSeconds: number,
  currentTime?: Date,
): Promise<WateringResponse[]> {
  const request: WateringRequest = {
    baseSeconds,
    mode: 'reverse',
    remainingSeconds,
    moistureSeconds,
    currentTime: currentTime ? formatLocalDateTime(currentTime) : undefined,
  }

  const response = await apiClient.post<WateringResponse[]>('/calculator/watering', request)
  return response.data
}

/**
 * 计算所有浇水策略的结果（反向模式 - 使用成熟时间点）
 */
export async function calculateAllStrategiesReverseByMatureTime(
  baseSeconds: number,
  matureTime: Date,
  moistureSeconds: number,
  currentTime?: Date,
): Promise<WateringResponse[]> {
  const request: WateringRequest = {
    baseSeconds,
    mode: 'reverse',
    matureTime: formatLocalDateTime(matureTime),
    moistureSeconds,
    currentTime: currentTime ? formatLocalDateTime(currentTime) : undefined,
  }

  const response = await apiClient.post<WateringResponse[]>('/calculator/watering', request)
  return response.data
}

/**
 * 计算单个浇水策略的结果
 */
export async function calculateSingleStrategy(
  baseSeconds: number,
  strategy: WaterStrategy,
  plantTime?: Date,
): Promise<WateringResponse> {
  const request: WateringRequest = {
    baseSeconds,
    strategy,
    plantTime: plantTime ? formatLocalDateTime(plantTime) : undefined,
  }

  const response = await apiClient.post<WateringResponse>('/calculator/watering/single', request)
  return response.data
}
