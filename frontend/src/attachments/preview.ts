import type { Attachment } from '../api/details'

export function previewKind(file: Attachment): 'image' | 'markdown' | 'text' | 'pdf' | undefined {
  const mime = file.mimeType.split(';')[0]?.toLowerCase()
  const extension = file.originalName.split('.').pop()?.toLowerCase()
  if (['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/bmp'].includes(mime || '')
      || ['jpg', 'jpeg', 'png', 'webp', 'gif', 'bmp'].includes(extension || '')) return 'image'
  if (extension === 'md' || mime === 'text/markdown') return 'markdown'
  if (extension === 'txt' || mime === 'text/plain') return 'text'
  if (extension === 'pdf' || mime === 'application/pdf') return 'pdf'
}
