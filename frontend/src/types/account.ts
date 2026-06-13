export type PlatformType =
  | 'ANDROID_QQ'
  | 'ANDROID_WECHAT'
  | 'IOS_QQ'
  | 'IOS_WECHAT'
  | ''

export const PLATFORM_LABELS: Record<Exclude<PlatformType, ''>, string> = {
  ANDROID_QQ: '安卓 QQ',
  ANDROID_WECHAT: '安卓微信',
  IOS_QQ: '苹果 QQ',
  IOS_WECHAT: '苹果微信',
}

export interface FarmProfile {
  farmLevel: number
  currentExp: number
  farmCoins: number
  stallLevel: number
  farmlandCount: number
  upgradedFarmlandCount: number
  note: string
}

export interface GameServer {
  id: string
  serverName: string
  farm: FarmProfile
}

export interface GameAccount {
  id: string
  displayName: string
  platformType: PlatformType
  regionCode: string
  servers: GameServer[]
}

export function createDefaultFarm(): FarmProfile {
  return {
    farmLevel: 1,
    currentExp: 0,
    farmCoins: 0,
    stallLevel: 0,
    farmlandCount: 3,
    upgradedFarmlandCount: 0,
    note: '',
  }
}

export function createId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}
