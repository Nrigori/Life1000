<script setup lang="ts">
import { ref } from 'vue'
import { createGoal } from '../api/goals'
import type { Category, LifeGoal } from '../api/goals'
import { ApiError, errorMessage } from '../api/http'
import { formatSlot } from '../goals/slots'
import ModalDialog from './ModalDialog.vue'

const props = defineProps<{ slotNo: number; categories: Category[] }>()
const emit = defineEmits<{ close: []; created: [goal: LifeGoal]; occupied: [] }>()
const title = ref('')
const categoryId = ref('')
const reason = ref('')
const saving = ref(false)
const error = ref('')

async function submit() {
  if (saving.value) return
  if (!title.value.trim()) {
    error.value = '请先写下标题。'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const goal = await createGoal(props.slotNo, {
      title: title.value.trim(),
      categoryId: categoryId.value ? Number(categoryId.value) : null,
      reason: reason.value.trim() || null,
    })
    emit('created', goal)
  } catch (cause) {
    error.value = errorMessage(cause)
    if (cause instanceof ApiError && cause.status === 409) emit('occupied')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ModalDialog :title="`第 ${formatSlot(slotNo)} 件`" :busy="saving" @close="emit('close')">
    <form class="entry-form" @submit.prevent="submit">
      <label for="goal-title">标题 <span class="required-note">必填</span></label>
      <input id="goal-title" v-model="title" autofocus required maxlength="255" :disabled="saving" />
      <label for="goal-category">分类 <span>可选</span></label>
      <select id="goal-category" v-model="categoryId" :disabled="saving">
        <option value="">不分类</option>
        <option v-for="category in categories" :key="category.id" :value="String(category.id)">{{ category.name }}</option>
      </select>
      <label for="goal-reason">为什么想做 <span>可选</span></label>
      <textarea id="goal-reason" v-model="reason" rows="4" :disabled="saving" />
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <div class="dialog-actions">
        <button type="button" :disabled="saving" @click="emit('close')">取消</button>
        <button type="submit" class="ink-button" :disabled="saving">{{ saving ? '正在写下…' : '写下它' }}</button>
      </div>
    </form>
  </ModalDialog>
</template>
