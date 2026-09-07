import type { LifeGoal } from '../api/goals'

export const SLOT_COUNT = 1000
export const RENDER_BATCH = 50

export function formatSlot(slotNo: number): string {
  return String(slotNo).padStart(3, '0')
}

// 未筛选时按永久的 1000 个位置补空白；筛选时仅展示命中项，但始终保留原始 slotNo。
export function makeRows(goals: LifeGoal[], filtered: boolean) {
  const bySlot = new Map(goals.map(goal => [goal.slotNo, goal]))
  const slots = filtered
    ? [...bySlot.keys()].sort((a, b) => a - b)
    : Array.from({ length: SLOT_COUNT }, (_, index) => index + 1)
  return slots.map(slotNo => ({ slotNo, goal: bySlot.get(slotNo) }))
}
