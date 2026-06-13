import { ref, watch } from 'vue'

/**
 * 作物图片工具
 *
 * 图片存放目录：frontend/public/images/crops/
 * 命名规则：{作物名}.png / {作物名}.webp / {作物名}.svg / {作物名}.jpg
 * 无对应图片时使用默认占位图 crop-placeholder.svg
 */

const CROP_IMAGE_BASE = '/images/crops/'
const PLACEHOLDER = `${CROP_IMAGE_BASE}crop-placeholder.svg`

/** 图鉴卡片图片承载框高度 */
export const CROP_CARD_FRAME_HEIGHT = 120

/** 图鉴卡片中作物图片显示高度（小于承载框，居中展示） */
export const CROP_CARD_IMAGE_HEIGHT = 80

/** 图鉴详情页图片承载框高度 */
export const CROP_DETAIL_FRAME_HEIGHT = 280

/** 图鉴详情页中作物图片显示高度（小于承载框，居中展示） */
export const CROP_DETAIL_IMAGE_HEIGHT = 200

/** 支持的图片扩展名优先级 */
const EXT_PRIORITY = ['png', 'webp', 'svg', 'jpg']

/**
 * 获取作物图片 URL（优先 png）
 * @param cropName 作物名称（与 crops.csv name 字段一致）
 * @returns 图片的绝对路径（基于 public 目录），默认返回 .png 路径
 */
export function getCropImage(cropName: string): string {
  if (!cropName) return PLACEHOLDER
  return `${CROP_IMAGE_BASE}${cropName}.png`
}

/**
 * 当作物图片加载失败时，依次尝试其他扩展名，最终回退到占位图
 * 用法：@error="handleCropImageError($event, cropName)"
 */
export function handleCropImageError(event: Event, cropName: string) {
  const img = event.target as HTMLImageElement
  if (!img || !cropName) return

  const currentSrc = img.src

  // 找当前扩展名在优先级中的位置，尝试下一个
  let nextExt: string | null = null
  for (let i = 0; i < EXT_PRIORITY.length; i++) {
    if (currentSrc.endsWith(`.${EXT_PRIORITY[i]}`)) {
      nextExt = EXT_PRIORITY[i + 1] ?? null
      break
    }
  }

  if (nextExt) {
    img.src = `${CROP_IMAGE_BASE}${cropName}.${nextExt}`
  } else {
    // 所有扩展名都试过了，用占位图
    img.src = PLACEHOLDER
    img.onerror = null // 防止死循环
  }
}

/** 已检测失败的图片路径缓存（应用生命周期内有效） */
const failedImages = new Set<string>()

/** 已解析的作物图片 URL 缓存（cropName → 可用 URL） */
const resolvedCache = new Map<string, string>()

/** 进行中的解析 Promise，避免同一 cropName 重复检测 */
const pendingResolves = new Map<string, Promise<string>>()

/** 图片加载并发上限（与浏览器同域连接数对齐） */
const MAX_CONCURRENT_LOADS = 6
let activeLoads = 0
const loadQueue: Array<() => void> = []

function acquireLoadSlot(): Promise<void> {
  if (activeLoads < MAX_CONCURRENT_LOADS) {
    activeLoads++
    return Promise.resolve()
  }
  return new Promise(resolve => loadQueue.push(() => { activeLoads++; resolve() }))
}

function releaseLoadSlot() {
  activeLoads--
  loadQueue.shift()?.()
}

/**
 * 解析作物图片 URL（异步预检测）
 * 尝试加载图片，失败则依次尝试其他扩展名，全部失败则返回占位图。
 * 结果会被缓存，同一 cropName 只检测一次；并发加载数受限，避免列表页同时发起大量请求。
 *
 * @param cropName 作物名称
 * @returns Promise<string> 可用的图片 URL
 */
export function resolveCropImage(cropName: string): Promise<string> {
  if (!cropName) return Promise.resolve(PLACEHOLDER)

  const cached = resolvedCache.get(cropName)
  if (cached) return Promise.resolve(cached)

  const pending = pendingResolves.get(cropName)
  if (pending) return pending

  const promise = resolveCropImageInternal(cropName).then(url => {
    resolvedCache.set(cropName, url)
    pendingResolves.delete(cropName)
    return url
  })

  pendingResolves.set(cropName, promise)
  return promise
}

function resolveCropImageInternal(cropName: string): Promise<string> {
  const candidates = EXT_PRIORITY.map(ext => `${CROP_IMAGE_BASE}${cropName}.${ext}`)
  const firstValid = candidates.find(url => !failedImages.has(url))
  if (!firstValid) return Promise.resolve(PLACEHOLDER)

  return tryLoadImage(firstValid).then(ok => {
    if (ok) return firstValid
    failedImages.add(firstValid)
    return tryNextCandidates(cropName, 1)
  })
}

/**
 * 依次尝试候选扩展名
 */
function tryNextCandidates(cropName: string, startIndex: number): Promise<string> {
  if (startIndex >= EXT_PRIORITY.length) return Promise.resolve(PLACEHOLDER)

  const url = `${CROP_IMAGE_BASE}${cropName}.${EXT_PRIORITY[startIndex]}`
  if (failedImages.has(url)) return tryNextCandidates(cropName, startIndex + 1)

  return tryLoadImage(url).then(ok => {
    if (ok) return url
    failedImages.add(url)
    return tryNextCandidates(cropName, startIndex + 1)
  })
}

/**
 * 尝试加载一张图片
 * @returns true 如果加载成功，false 如果加载失败
 */
function tryLoadImage(url: string): Promise<boolean> {
  return acquireLoadSlot().then(() =>
    new Promise<boolean>(resolve => {
      const img = new Image()
      const finish = (ok: boolean) => {
        releaseLoadSlot()
        resolve(ok)
      }
      img.onload = () => finish(true)
      img.onerror = () => finish(false)
      img.src = url
    })
  )
}

/**
 * Vue composable：获取作物图片的响应式 URL
 * 先同步返回 png 路径（避免闪烁），异步检测后更新为实际可用 URL 或占位图。
 *
 * @param cropName 作物名称（Ref 或 getter）
 * @returns Ref<string> 响应式图片 URL
 */
export function useCropImage(cropName: () => string) {
  const imageUrl = ref(getCropImage(cropName()))
  const resolving = ref(true)
  let watchVersion = 0

  watch(
    () => cropName(),
    async (name, _oldName, onCleanup) => {
      const version = ++watchVersion
      imageUrl.value = getCropImage(name)
      resolving.value = true

      let cancelled = false
      onCleanup(() => { cancelled = true })

      const resolved = await resolveCropImage(name)

      if (!cancelled && cropName() === name && watchVersion === version) {
        imageUrl.value = resolved
        resolving.value = false
      }
    },
    { immediate: true }
  )

  return { imageUrl, resolving }
}
