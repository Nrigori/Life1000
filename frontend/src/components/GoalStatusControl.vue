<script setup lang="ts">
import type { ActiveGoalStatus } from '../api/goals'
import { formatSlot } from '../goals/slots'

const props = defineProps<{ slotNo: number; status: ActiveGoalStatus; busy?: boolean }>()
const emit = defineEmits<{ change: [status: ActiveGoalStatus] }>()

function change(event: Event) {
  const value = (event.currentTarget as HTMLSelectElement).value as ActiveGoalStatus
  if (value !== props.status) emit('change', value)
}
</script>

<template>
  <label class="goal-status-control" @click.stop @mousedown.stop>
    <span class="sr-only">切换第 {{ formatSlot(slotNo) }} 件状态</span>
    <select :value="status" :disabled="busy" :aria-label="`第 ${formatSlot(slotNo)} 件状态`" @change="change">
      <option value="NOT_STARTED">○ 未开始</option>
      <option value="IN_PROGRESS">◐ 进行中</option>
    </select>
  </label>
</template>
