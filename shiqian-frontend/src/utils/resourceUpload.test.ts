import { describe, expect, it, vi } from 'vitest'
import {
  MAX_RESOURCE_FILE_SIZE,
  uploadFilesByTier,
  uploadTierForSize,
  validateResourceFile
} from './resourceUpload'

function file(name: string, size: number) {
  return { name, size } as File
}

describe('resource upload rules', () => {
  it('accepts documents and source code but rejects unsupported files', () => {
    expect(validateResourceFile(file('notes.txt', 10))).toBe('')
    expect(validateResourceFile(file('Main.java', 10))).toBe('')
    expect(validateResourceFile(file('virus.exe', 10))).toContain('不支持')
    expect(validateResourceFile(file('large.pdf', MAX_RESOURCE_FILE_SIZE + 1))).toContain('超过 50MB')
  })

  it('uses graded concurrency by file size', () => {
    expect(uploadTierForSize(1024).concurrency).toBe(4)
    expect(uploadTierForSize(5 * 1024 * 1024).concurrency).toBe(2)
    expect(uploadTierForSize(20 * 1024 * 1024).concurrency).toBe(1)
  })

  it('runs up to four small uploads concurrently', async () => {
    let active = 0
    let maxActive = 0
    const files = Array.from({ length: 6 }, (_, index) => file(`small-${index}.txt`, 100))

    await uploadFilesByTier(files, {
      worker: async current => {
        active += 1
        maxActive = Math.max(maxActive, active)
        await new Promise(resolve => setTimeout(resolve, 5))
        active -= 1
        return current.name
      }
    })

    expect(maxActive).toBe(4)
  })

  it('prioritizes small files and retries a transient failure once', async () => {
    const attempts = new Map<string, number>()
    const order: string[] = []
    const onRetry = vi.fn()
    const files = [file('large.pdf', 11 * 1024 * 1024), file('small.txt', 20)]

    const result = await uploadFilesByTier(files, {
      retries: 1,
      onRetry,
      worker: async (current, onProgress) => {
        order.push(current.name)
        onProgress(100)
        const count = (attempts.get(current.name) || 0) + 1
        attempts.set(current.name, count)
        if (current.name === 'small.txt' && count === 1) throw new Error('temporary')
        return current.name
      }
    })

    expect(order.slice(0, 2)).toEqual(['small.txt', 'small.txt'])
    expect(result.results).toEqual(['large.pdf', 'small.txt'])
    expect(result.failures).toHaveLength(0)
    expect(onRetry).toHaveBeenCalledOnce()
  })
})
