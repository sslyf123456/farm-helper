import client from './client'

export type CsvRow = Record<string, string>

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
