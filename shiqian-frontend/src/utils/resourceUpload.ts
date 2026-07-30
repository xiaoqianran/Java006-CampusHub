export const MAX_RESOURCE_FILE_SIZE = 50 * 1024 * 1024
export const MAX_RESOURCE_FILE_COUNT = 10
export const SMALL_FILE_LIMIT = 2 * 1024 * 1024
export const MEDIUM_FILE_LIMIT = 10 * 1024 * 1024

export const RESOURCE_FILE_ACCEPT = [
  '.pdf', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx',
  '.txt', '.md',
  '.jpg', '.jpeg', '.png', '.gif',
  '.zip', '.rar', '.7z',
  '.java', '.py', '.js', '.ts', '.vue', '.c', '.cpp', '.h',
  '.go', '.rs', '.sql', '.json', '.xml', '.yaml', '.yml',
  '.html', '.css', '.sh'
].join(',')

const ALLOWED_EXTENSIONS = new Set(
  RESOURCE_FILE_ACCEPT.split(',').map(item => item.slice(1))
)

export type UploadTierName = '小文件' | '中等文件' | '大文件'

interface UploadTier {
  name: UploadTierName
  concurrency: number
  matches: (size: number) => boolean
}

const UPLOAD_TIERS: UploadTier[] = [
  { name: '小文件', concurrency: 4, matches: size => size <= SMALL_FILE_LIMIT },
  { name: '中等文件', concurrency: 2, matches: size => size > SMALL_FILE_LIMIT && size <= MEDIUM_FILE_LIMIT },
  { name: '大文件', concurrency: 1, matches: size => size > MEDIUM_FILE_LIMIT }
]

export function fileExtension(fileName: string) {
  const index = fileName.lastIndexOf('.')
  return index >= 0 ? fileName.slice(index + 1).toLowerCase() : ''
}

export function validateResourceFile(file: File) {
  if (!file.size) return `${file.name}：空文件不能上传`
  if (file.size > MAX_RESOURCE_FILE_SIZE) return `${file.name}：超过 50MB`
  if (!ALLOWED_EXTENSIONS.has(fileExtension(file.name))) {
    return `${file.name}：不支持此文件类型`
  }
  return ''
}

export function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

export function uploadTierForSize(size: number) {
  return UPLOAD_TIERS.find(tier => tier.matches(size)) || UPLOAD_TIERS[UPLOAD_TIERS.length - 1]
}

interface TieredUploadOptions<T> {
  signal?: AbortSignal
  retries?: number
  worker: (file: File, onProgress: (percentage: number) => void) => Promise<T>
  onProgress?: (percentage: number) => void
  onTierChange?: (tier: UploadTierName, concurrency: number) => void
  onFileUploaded?: (file: File, result: T) => void
  onRetry?: (file: File) => void
}

export interface TieredUploadFailure {
  file: File
  error: unknown
}

export interface TieredUploadResult<T> {
  results: T[]
  failures: TieredUploadFailure[]
}

function abortError() {
  return new DOMException('上传已取消', 'AbortError')
}

function waitBeforeRetry(signal?: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal?.aborted) {
      reject(abortError())
      return
    }
    const abort = () => {
      clearTimeout(timer)
      reject(abortError())
    }
    const timer = window.setTimeout(() => {
      signal?.removeEventListener('abort', abort)
      resolve()
    }, 400)
    signal?.addEventListener('abort', abort, { once: true })
  })
}

export async function uploadFilesByTier<T>(
  files: File[],
  options: TieredUploadOptions<T>
): Promise<TieredUploadResult<T>> {
  if (!files.length) return { results: [], failures: [] }

  const retries = options.retries ?? 1
  const indexedFiles = files.map((file, index) => ({ file, index }))
  const progress = new Array(files.length).fill(0)
  const totalBytes = files.reduce((sum, file) => sum + Math.max(file.size, 1), 0)
  const results: Array<{ index: number; value: T }> = []
  const failures: TieredUploadFailure[] = []

  const updateProgress = (index: number, percentage: number) => {
    progress[index] = Math.min(100, Math.max(0, percentage))
    const uploadedBytes = files.reduce(
      (sum, file, fileIndex) => sum + Math.max(file.size, 1) * progress[fileIndex] / 100,
      0
    )
    options.onProgress?.(Math.round(uploadedBytes / totalBytes * 100))
  }

  for (const tier of UPLOAD_TIERS) {
    const queue = indexedFiles.filter(item => tier.matches(item.file.size))
    if (!queue.length) continue
    if (options.signal?.aborted) throw abortError()

    options.onTierChange?.(tier.name, tier.concurrency)
    let cursor = 0

    const runner = async () => {
      while (cursor < queue.length) {
        if (options.signal?.aborted) throw abortError()
        const current = queue[cursor++]
        let lastError: unknown

        for (let attempt = 0; attempt <= retries; attempt += 1) {
          try {
            const value = await options.worker(
              current.file,
              percentage => updateProgress(current.index, percentage)
            )
            updateProgress(current.index, 100)
            results.push({ index: current.index, value })
            options.onFileUploaded?.(current.file, value)
            lastError = undefined
            break
          } catch (error) {
            if (error instanceof DOMException && error.name === 'AbortError') throw error
            lastError = error
            if (attempt < retries) {
              updateProgress(current.index, 0)
              options.onRetry?.(current.file)
              await waitBeforeRetry(options.signal)
            }
          }
        }

        if (lastError !== undefined) {
          failures.push({ file: current.file, error: lastError })
        }
      }
    }

    await Promise.all(
      Array.from({ length: Math.min(tier.concurrency, queue.length) }, () => runner())
    )
  }

  return {
    results: results.sort((a, b) => a.index - b.index).map(item => item.value),
    failures
  }
}
