<script setup lang="ts">
import type { LifeGoal } from '../api/goals'
import { statusLabels } from '../api/goals'
import { formatSlot } from '../goals/slots'

defineProps<{ slotNo: number; goal?: LifeGoal; categoryName?: string; view: 'cards' | 'list' }>()
const emit = defineEmits<{ create: [slotNo: number] }>()
</script>

<template>
  <li class="goal-tile" :class="[view === 'list' ? 'goal-row' : 'goal-card', { 'blank-tile': !goal }]" :data-slot="slotNo">
    <button v-if="!goal" class="blank-open" type="button" :aria-label="`第 ${formatSlot(slotNo)} 件，尚未写下`" @click="emit('create', slotNo)">
      <span class="slot-number">{{ formatSlot(slotNo) }}</span>
      <span class="blank-plus" aria-hidden="true">＋</span>
      <span class="blank-caption">尚未写下</span>
    </button>
    <template v-else>
      <RouterLink class="goal-open" :to="`/goals/${slotNo}`" :aria-label="`第 ${formatSlot(slotNo)} 件：${goal.title}`">
        <span class="slot-number">{{ formatSlot(slotNo) }}</span>
        <span v-if="view === 'cards'" class="cover-empty">
          <span class="cover-symbol" aria-hidden="true">✦</span><span>尚无影像</span>
        </span>
        <h2 class="goal-title" :title="goal.title">{{ goal.title }}</h2>
        <span class="goal-category">{{ categoryName || '未分类' }}</span>
        <span class="goal-status" :class="{ 'is-completed': goal.status === 'COMPLETED' }">{{ statusLabels[goal.status] }}</span>
      </RouterLink>
      <button class="complete-entry" type="button" disabled
        :aria-label="goal.status === 'COMPLETED' ? '已完成' : '完成入口暂未开放'"
        :title="goal.status === 'COMPLETED' ? '已完成' : '完成记录尚未开放'">
        {{ goal.status === 'COMPLETED' ? '✓' : '○' }}
      </button>
    </template>
  </li>
</template>
