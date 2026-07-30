import { describe, expect, it } from 'vitest'
import {
  attachmentPreviewKind,
  storedResourceFilePath
} from './attachmentPreview'

describe('attachment preview helpers', () => {
  it('recognizes common preview types', () => {
    expect(attachmentPreviewKind('lesson.pdf')).toBe('pdf')
    expect(attachmentPreviewKind('README.md')).toBe('markdown')
    expect(attachmentPreviewKind('notes.txt')).toBe('text')
    expect(attachmentPreviewKind('source.java')).toBe('text')
    expect(attachmentPreviewKind('assets.zip')).toBe('archive')
    expect(attachmentPreviewKind('archive.rar')).toBe('unsupported')
  })

  it('extracts only paths stored by the resource service', () => {
    expect(storedResourceFilePath('/api/resource/files/7/demo.txt')).toBe('7/demo.txt')
    expect(storedResourceFilePath('https://example.com/demo.txt')).toBe('')
  })
})
