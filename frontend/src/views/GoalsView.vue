<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { getCategories, getGoals, searchGoals, updateGoalStatus } from '../api/goals'
import type { ActiveGoalStatus, Category, GoalFilters, LifeGoal } from '../api/goals'
import { errorMessage } from '../api/http'
import { formatSlot, makeRows, RENDER_BATCH } from '../goals/slots'
import GoalTile from '../components/GoalTile.vue'
import CompletionDialog from '../components/CompletionDialog.vue'
import CompletionFeedback from '../components/CompletionFeedback.vue'
import GoalCreateDialog from '../components/GoalCreateDialog.vue'
import '../styles/goals.css'

const completing = ref<LifeGoal>()
const feedback = ref<number>()
async function completed() {
  feedback.value = completing.value!.slotNo
  completing.value = undefined
  await load()
}
const view = ref<'cards' | 'list'>('cards')
const filters = reactive<GoalFilters>({ keyword: '', categoryId: '', status: '' })
const filtered = computed(() => Boolean(filters.keyword.trim() || filters.categoryId || filters.status))
const categories = ref<Category[]>([])
const records = ref<LifeGoal[]>([])
const rows = computed(() => makeRows(records.value, filtered.value))
// 数据位置完整保留，只分批增加 DOM 数量；连续滚动不会改变编号，也不需要后台分页 UI。
const visibleCount = ref(RENDER_BATCH)
const visibleRows = computed(() => rows.value.slice(0, visibleCount.value))
const categoryNames = computed(() => new Map(categories.value.map(item => [item.id, item.name])))
const loading = ref(true)
const error = ref('')
const statusError = ref('')
const statusBusy = ref<Set<number>>(new Set())
const createSlot = ref<number | null>(null)
const sentinel = ref<HTMLElement>()
let observer: IntersectionObserver | undefined
let controller: AbortController | undefined
let revision = 0
let timer: ReturnType<typeof setTimeout> | undefined

// 取消请求减少无用工作，revision 再兜底拒绝旧响应，防止较慢的搜索覆盖较新的筛选结果。
async function load() {
  const current = ++revision
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const query = { ...filters }
    const [categoryResult, goalResult] = await Promise.all([
      getCategories(controller.signal),
      filtered.value ? searchGoals(query, controller.signal) : getGoals(controller.signal),
    ])
    if (current !== revision) return
    categories.value = categoryResult
    records.value = goalResult
  } catch (cause) {
    if (current !== revision) return
    error.value = errorMessage(cause)
  } finally {
    if (current === revision) loading.value = false
  }
}

// 输入变化时立即作废旧请求，再延迟查询；不能等防抖结束后才阻止旧响应更新页面。
watch(filters, () => {
  clearTimeout(timer)
  ++revision
  controller?.abort()
  loading.value = true
  error.value = ''
  visibleCount.value = RENDER_BATCH
  timer = setTimeout(load, 250)
})
function clearFilters() {
  Object.assign(filters, { keyword: '', categoryId: '', status: '' })
}
function showMore() {
  visibleCount.value = Math.min(visibleCount.value + RENDER_BATCH, rows.value.length)
}
watch(sentinel, (element) => {
  observer?.disconnect()
  if (!element || !('IntersectionObserver' in window)) return
  observer = new IntersectionObserver(entries => {
    if (entries.some(entry => entry.isIntersecting) && !loading.value && !error.value) showMore()
  }, { rootMargin: '240px' })
  observer.observe(element)
})
function created(goal: LifeGoal) {
  records.value = [...records.value.filter(item => item.slotNo !== goal.slotNo), goal]
  createSlot.value = null
}
async function changeStatus(goal: LifeGoal, status: ActiveGoalStatus) {
  if (goal.status === 'COMPLETED' || goal.status === status || statusBusy.value.has(goal.slotNo)) return
  const original = goal
  statusError.value = ''
  statusBusy.value = new Set(statusBusy.value).add(goal.slotNo)
  records.value = records.value.map(item => item.slotNo === goal.slotNo ? { ...item, status } : item)
  try {
    const updated = await updateGoalStatus(goal.slotNo, status)
    records.value = records.value.map(item => item.slotNo === goal.slotNo ? updated : item)
    // 在请求确认前保留卡片；成功后再移出不匹配的状态筛选，避免失败时消失又重现。
    if (filters.status && updated.status !== filters.status) {
      records.value = records.value.filter(item => item.slotNo !== goal.slotNo)
    }
  } catch (cause) {
    records.value = records.value.map(item => item.slotNo === goal.slotNo ? original : item)
    statusError.value = `第 ${formatSlot(goal.slotNo)} 件状态未能更新：${errorMessage(cause)}`
  } finally {
    const next = new Set(statusBusy.value)
    next.delete(goal.slotNo)
    statusBusy.value = next
  }
}
onMounted(load)
onBeforeUnmount(() => {
  ++revision
  controller?.abort()
  clearTimeout(timer)
  observer?.disconnect()
})
</script>

<template>
  <section class="goals-page" aria-labelledby="goals-heading">
    <div class="goals-heading">
      <h1 id="goals-heading">人生千事</h1>
      <p>001 — 1000 <span>· 慢慢写下，慢慢经历。</span></p>
    </div>
    <div class="goals-toolbar">
      <label class="search-field">
        <span class="sr-only">搜索事项标题</span>
        <span aria-hidden="true">⌕</span>
        <input v-model="filters.keyword" type="search" maxlength="255" placeholder="搜索想做的事…" />
      </label>
      <label class="filter-field"><span class="sr-only">分类筛选</span>
        <select v-model="filters.categoryId">
          <option value="">全部分类</option>
          <option v-for="category in categories" :key="category.id" :value="String(category.id)">{{ category.name }}</option>
        </select>
      </label>
      <label class="filter-field"><span class="sr-only">状态筛选</span>
        <select v-model="filters.status">
          <option value="">全部状态</option>
          <option value="NOT_STARTED">未开始</option>
          <option value="IN_PROGRESS">进行中</option>
          <option value="COMPLETED">已完成</option>
        </select>
      </label>
      <div class="view-toggle" role="group" aria-label="显示方式">
        <button type="button" :aria-pressed="view === 'cards'" @click="view = 'cards'">卡片</button>
        <span aria-hidden="true">|</span>
        <button type="button" :aria-pressed="view === 'list'" @click="view = 'list'">列表</button>
      </div>
    </div>
    <div class="goals-content" :aria-busy="loading">
      <p v-if="loading" class="page-message" role="status">正在翻阅…</p>
      <div v-else-if="error" class="page-message">
        <p role="alert">{{ error }}</p><button type="button" @click="load">重新读取</button>
      </div>
      <template v-else>
        <p v-if="statusError" class="status-action-error" role="alert">{{ statusError }}</p>
        <div v-if="filtered" class="filter-summary">
          <span>找到 {{ rows.length }} 件事</span><button type="button" @click="clearFilters">清除筛选</button>
        </div>
        <div v-if="!rows.length" class="page-message"><p>没有找到符合条件的事项。</p></div>
        <ol v-else class="goals-collection" :class="view === 'cards' ? 'goals-grid' : 'goals-list'" aria-label="人生千事固定位置">
          <GoalTile v-for="row in visibleRows" :key="row.slotNo" :slot-no="row.slotNo" :goal="row.goal"
            :category-name="row.goal?.categoryId ? categoryNames.get(row.goal.categoryId) : undefined"
            :view="view" :status-busy="statusBusy.has(row.slotNo)" @create="createSlot = $event"
            @complete="completing = $event" @change-status="changeStatus" />
        </ol>
        <div v-if="visibleCount < rows.length" ref="sentinel" class="scroll-sentinel">
          <button type="button" @click="showMore">继续向下展开</button>
        </div>
        <p v-else-if="rows.length" class="collection-end">{{ filtered ? '以上是符合条件的事项。' : '1000 · 空白也属于这里。' }}</p>
      </template>
    </div>
    <CompletionDialog v-if="completing" :goal="completing" @close="completing = undefined" @saved="completed" />
    <CompletionFeedback v-if="feedback !== undefined" :slot="feedback" @expired="feedback = undefined" />
    <GoalCreateDialog v-if="createSlot !== null" :slot-no="createSlot" :categories="categories"
      @close="createSlot = null" @created="created" @occupied="load" />
  </section>
</template>
