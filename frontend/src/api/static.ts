import client from './client'

/** API 返回的行类型：数值字段为 number，文本字段为 string，空值可能为 null */
export type CsvRow = Record<string, string | number | null>

export const fetchFarmLevels = () =>
  client.get<CsvRow[]>('/static/farm-levels').then((r) => r.data)

export const fetchStall = () =>
  client.get<CsvRow[]>('/static/stall').then((r) => r.data)

export const fetchLand = () =>
  client.get<CsvRow[]>('/static/land').then((r) => r.data)

export const fetchCrops = () =>
  client.get<CsvRow[]>('/static/crops').then((r) => r.data)

export const fetchCropDetail = (name: string) =>
  client.get<CsvRow>(`/static/crops/${encodeURIComponent(name)}`).then((r) => r.data)

export const fetchCultivation = (crop?: string) =>
  client
    .get<CsvRow[]>('/static/cultivation', { params: crop ? { crop } : {} })
    .then((r) => r.data)

export const fetchMutationRates = () =>
  client.get<CsvRow[]>('/static/mutation-rates').then((r) => r.data)

export const fetchRewards = () =>
  client.get<CsvRow[]>('/static/rewards').then((r) => r.data)
