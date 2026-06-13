import type { CsvRow } from '@/api/static'

export type CropCategory = 'exp' | 'high_coin' | 'hero'

export function getCropCategory(crop: CsvRow): CropCategory {
  const harvestTime = crop['harvest_time']?.trim() ?? ''
  const hero = crop['mutation_hero']?.trim() ?? ''
  const exp = crop['exp_gain']?.trim() ?? ''

  if (harvestTime === '32h' || hero) {
    return 'hero'
  }
  if (harvestTime === '16h' && exp === '1') {
    return 'high_coin'
  }
  return 'exp'
}

export const CROP_CATEGORY_LABEL: Record<CropCategory, string> = {
  exp: '经验作物',
  high_coin: '高农场币作物',
  hero: '英雄作物',
}
