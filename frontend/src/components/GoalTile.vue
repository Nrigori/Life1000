<script setup lang="ts">
import type { LifeGoal } from '../api/goals'
import { statusLabels } from '../api/goals'
import GoalCover from './GoalCover.vue'
import GoalStatusControl from './GoalStatusControl.vue'
import { formatSlot } from '../goals/slots'

defineProps<{ slotNo: number; goal?: LifeGoal; categoryName?: string; view: 'cards' | 'list'; statusBusy?: boolean }>()
const emit = defineEmits<{
  create: [slotNo: number]
  complete: [goal: LifeGoal]
  changeStatus: [goal: LifeGoal, status: 'NOT_STARTED' | 'IN_PROGRESS']
}>()
</script>

<template>
  <li class="goal-tile" :class="[view === 'list' ? 'goal-row' : 'goal-card', { 'blank-tile': !goal, 'memory-tile': goal?.status === 'COMPLETED' }]" :data-slot="slotNo">
    <button v-if="!goal" class="blank-open" type="button" :aria-label="`第 ${formatSlot(slotNo)} 件，尚未写下`" @click="emit('create', slotNo)">
      <span class="slot-number">{{ formatSlot(slotNo) }}</span>
      <span class="blank-plus" aria-hidden="true">＋</span>
      <span class="blank-caption">尚未写下</span>
    </button>
    <template v-else>
      <RouterLink class="goal-open" :to="`/goals/${slotNo}`" :aria-label="`第 ${formatSlot(slotNo)} 件：${goal.title}`">
        <span class="slot-number">{{ formatSlot(slotNo) }}</span>
        <GoalCover v-if="view === 'cards'" :slot="slotNo" />
        <h2 class="goal-title" :title="goal.title">{{ goal.title }}</h2>
        <span class="goal-category">{{ categoryName || '不分类' }}</span>
        <span v-if="goal.status === 'COMPLETED'" class="goal-status is-completed">{{ goal.completedDate ? goal.completedDate.replaceAll('-', '.') : statusLabels[goal.status] }}</span>
        <span v-else class="goal-status-placeholder" aria-hidden="true" />
      </RouterLink>
      <GoalStatusControl v-if="goal.status !== 'COMPLETED'" :slot-no="slotNo" :status="goal.status" :busy="statusBusy"
        @change="emit('changeStatus', goal, $event)" />
      <button class="complete-entry" type="button" :disabled="goal.status === 'COMPLETED' || statusBusy" @click="emit('complete', goal)"
        :aria-label="goal.status === 'COMPLETED' ? '已完成' : '标记为完成'"
        :title="goal.status === 'COMPLETED' ? '已完成' : '标记为完成'">
        {{ goal.status === 'COMPLETED' ? '✓' : '○' }}
      </button>
    </template>
  </li>
</template>
