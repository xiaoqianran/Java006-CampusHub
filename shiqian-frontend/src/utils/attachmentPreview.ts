import { buildApiUrl } from '@/api/client'

export type AttachmentPreviewKind = 'pdf' | 'markdown' | 'text' | 'image' | 'archive' | 'unsupported'

const TEXT_EXTENSIONS = new Set([
  'txt', 'java', 'py', 'js', 'ts', 'vue', 'c', 'cpp', 'h',
  'go', 'rs', 'sql', 'json', 'xml', 'yaml', 'yml', 'html', 'css', 'sh'
])
const IMAGE_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'gif'])

export function attachmentExtension(fileName = '', fileUrl = '') {
  const source = fileName || fileUrl.split('?')[0]
  const index = source.lastIndexOf('.')
  return index >= 0 ? source.slice(index + 1).toLowerCase() : ''
}

export function attachmentPreviewKind(fileName = '', fileUrl = ''): AttachmentPreviewKind {
  const extension = attachmentExtension(fileName, fileUrl)
  if (extension === 'pdf') return 'pdf'
  if (extension === 'md') return 'markdown'
  if (TEXT_EXTENSIONS.has(extension)) return 'text'
  if (IMAGE_EXTENSIONS.has(extension)) return 'image'
  if (extension === 'zip') return 'archive'
  return 'unsupported'
}

export function storedResourceFilePath(fileUrl: string) {
  try {
    const url = new URL(buildApiUrl(fileUrl), window.location.origin)
    const prefix = '/api/resource/files/'
    if (!url.pathname.startsWith(prefix)) return ''
    return decodeURIComponent(url.pathname.slice(prefix.length))
  } catch {
    return ''
  }
}

export function inlineResourceFileUrl(fileUrl: string) {
  return buildApiUrl(fileUrl, { inline: true })
}
