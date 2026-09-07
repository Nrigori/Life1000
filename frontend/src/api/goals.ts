import { request } from './http'

export type GoalStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'

export interface LifeGoal {
  id: number
  slotNo: number
  title: string
  categoryId: number | null
  status: GoalStatus
  reason: string | null
  coverAttachmentId: number | null
}

export interface Category {
  id: number
  name: string
  sortOrder: number
}

export interface GoalInput {
  title: string
  categoryId: number | null
  reason: string | null
}

export interface GoalFilters {
  keyword: string
  categoryId: string
  status: GoalStatus | ''
}

export const statusLabels: Record<GoalStatus, string> = {
  NOT_STARTED: '未开始', IN_PROGRESS: '进行中', COMPLETED: '已完成',
}

export function getCategories(signal?: AbortSignal) {
  return request<Category[]>('/categories', { signal })
}

export function getGoals(signal?: AbortSignal) {
  return request<LifeGoal[]>('/goals/range?fromSlot=1&toSlot=1000', { signal })
}

export function searchGoals(filters: GoalFilters, signal?: AbortSignal) {
  const query = new URLSearchParams()
  if (filters.keyword.trim()) query.set('keyword', filters.keyword.trim())
  if (filters.categoryId) query.set('categoryId', filters.categoryId)
  if (filters.status) query.set('status', filters.status)
  return request<LifeGoal[]>(`/goals/search?${query}`, { signal })
}

export function getGoal(slotNo: number) {
  return request<LifeGoal>(`/goals/${slotNo}`)
}

export function createGoal(slotNo: number, input: GoalInput) {
  return request<LifeGoal>(`/goals/${slotNo}`, { method: 'POST', body: JSON.stringify(input) })
}

export function deleteGoal(slotNo: number) {
  return request<void>(`/goals/${slotNo}`, { method: 'DELETE' })
}
