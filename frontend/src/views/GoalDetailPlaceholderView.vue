<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { deleteGoal, getGoal } from '../api/goals'
import type { LifeGoal } from '../api/goals'
import { errorMessage } from '../api/http'
import { formatSlot } from '../goals/slots'
import ModalDialog from '../components/ModalDialog.vue'

const route = useRoute()
const router = useRouter()
const goal = ref<LifeGoal>()
const error = ref('')
const loading = ref(false)
const confirming = ref(false)
const deleting = ref(false)
const deleteError = ref('')
let revision = 0

async function load() {
  const current = ++revision
  loading.value = true
  error.value = ''
  goal.value = undefined
  const slotNo = Number(route.params.slotNo)
  if (!Number.isInteger(slotNo) || slotNo < 1 || slotNo > 1000) {
    error.value = '编号必须为 001～1000。'
    loading.value = false
    return
  }
  try {
    const result = await getGoal(slotNo)
    if (current === revision) goal.value = result
  } catch (cause) {
    if (current === revision) error.value = errorMessage(cause)
  } finally {
    if (current === revision) loading.value = false
  }
}

async function remove() {
  if (!goal.value || deleting.value) return
  deleting.value = true
  deleteError.value = ''
  try {
    await deleteGoal(goal.value.slotNo)
    confirming.value = false
    await router.push('/goals')
  } catch (cause) {
    deleteError.value = errorMessage(cause)
  } finally {
    deleting.value = false
  }
}
watch(() => route.params.slotNo, load, { immediate: true })
</script>

<template>
  <section class="paper detail-placeholder">
    <RouterLink class="back-link" to="/goals">← 返回人生千事</RouterLink>
    <p v-if="loading" role="status">正在翻阅…</p>
    <div v-else-if="error"><p class="form-error" role="alert">{{ error }}</p><button @click="load">重新读取</button></div>
    <template v-else-if="goal">
      <p class="eyebrow">第 {{ formatSlot(goal.slotNo) }} 件</p>
      <h1>{{ goal.title }}</h1>
      <p class="placeholder-copy">这一页，留待以后慢慢展开。</p>
      <button class="clear-slot-button" type="button" @click="confirming = true">清空这个编号</button>
    </template>
    <ModalDialog v-if="confirming && goal" :title="`清空第 ${formatSlot(goal.slotNo)} 件？`" :busy="deleting" @close="confirming = false">
      <p>删除事项将同时删除对应记录和附件。</p>
      <p>这个编号将重新成为空白，其他编号保持原样。</p>
      <p v-if="deleteError" class="form-error" role="alert">{{ deleteError }}</p>
      <div class="dialog-actions">
        <button type="button" autofocus :disabled="deleting" @click="confirming = false">取消</button>
        <button type="button" class="ink-button" :disabled="deleting" @click="remove">{{ deleting ? '正在清空…' : '确认清空' }}</button>
      </div>
    </ModalDialog>
  </section>
</template>
