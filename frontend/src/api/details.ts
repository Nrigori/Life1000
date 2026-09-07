import { fetchApi, request } from './http'
export interface CheckItem { id: number; content: string; completed: boolean; sortOrder: number }
export interface GoalRecord { id: number; content: string; recordDate: string }
export interface Attachment {
  id: number; recordId: number | null; stage: 'GENERAL' | 'PROCESS' | 'COMPLETION'; originalName: string
  fileSize: number; mimeType: string; isImage: boolean; allowHomeBackground: boolean
}
export const readChecks = (slot: number) => request<CheckItem[]>(`/goals/${slot}/check-items`)
export const readRecords = (slot: number) => request<GoalRecord[]>(`/goals/${slot}/records`)
export const readAttachments = (slot: number) => request<Attachment[]>(`/goals/${slot}/attachments`)
export const readCover = (slot: number) => request<Attachment | undefined>(`/goals/${slot}/cover`)
export const write = <T>(path: string, method: string, body?: unknown) =>
  request<T>(path, { method, body: body === undefined ? undefined : JSON.stringify(body) })
export async function upload(slot: number, file: File, recordId?: number) {
  const body = new FormData()
  body.append('file', file)
  body.append('stage', recordId === undefined ? 'GENERAL' : 'PROCESS')
  if (recordId !== undefined) body.append('recordId', String(recordId))
  return request<Attachment>(`/goals/${slot}/attachments`, { method: 'POST', body })
}
export async function fileBlob(id: number, download = false, signal?: AbortSignal) {
  return (await fetchApi(`/attachments/${id}/content?download=${download}`, { signal })).blob()
}
export async function downloadFile(value: Attachment) {
  const url = URL.createObjectURL(await fileBlob(value.id, true))
  const link = document.createElement('a')
  link.href = url; link.download = value.originalName
  document.body.append(link); link.click(); link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
export const fileSize = (size: number) => size < 1024 ? size + ' B' : size < 1048576
  ? (size / 1024).toFixed(1) + ' KB' : size < 1073741824 ? (size / 1048576).toFixed(1) + ' MB' : (size / 1073741824).toFixed(2) + ' GB'
