import { request } from './http'
export interface Completion {
  id: number; goalId: number; completedDate: string; completionNote: string | null
  rating: number | null; statusBeforeCompletion: 'NOT_STARTED' | 'IN_PROGRESS'
}
export interface CompletionInput { completedDate: string; completionNote: string | null; rating: number | null }
export interface TimelineYear { year: number; count: number }
export interface TimelineEntry { year: number; completedDate: string; slotNo: number; title: string }
export const readCompletion = (slot: number) => request<Completion | undefined>(`/goals/${slot}/completion`)
export const undoCompletion = (slot: number) => request<void>(`/goals/${slot}/uncomplete`, { method: 'POST' })
export function saveCompletion(slot: number, input: CompletionInput, files: File[], editing: boolean) {
  let body: BodyInit = JSON.stringify(input)
  if (files.length) {
    const form = new FormData()
    form.append('completion', new Blob([JSON.stringify(input)], { type: 'application/json' }))
    for (const file of files) form.append('files', file)
    body = form
  }
  return request<Completion>(`/goals/${slot}/${editing ? 'completion' : 'complete'}`, { method: editing ? 'PUT' : 'POST', body })
}
export const readTimeline = () => request<TimelineYear[]>('/timeline')
export const readYear = (year: number) => request<TimelineEntry[]>(`/timeline/${year}`)
export const localToday = () => {
  const value = new Date()
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
}
