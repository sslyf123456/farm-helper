import type { CsvRow } from '@/api/static'

export type CropCategory = 'exp' | 'high_coin' | 'hero'

export function getCropCategory(crop: CsvRow): CropCategory {
  const harvestSeconds = Number(crop['harvest_time'] ?? 0)
  const hero = String(crop['mutation_hero'] ?? '').trim()
  const exp = Number(crop['exp_gain'] ?? 0)

  // 32h = 115200s
  if (harvestSeconds === 115200 || hero) {
    return 'hero'
  }
  // 16h = 57600s
  if (harvestSeconds === 57600 && exp === 1) {
    return 'high_coin'
  }
  return 'exp'
}

export const CROP_CATEGORY_LABEL: Record<CropCategory, string> = {
  exp: '经验作物',
  high_coin: '高农场币作物',
  hero: '英雄作物',
}
