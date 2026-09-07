<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getGoal, getCategories, deleteGoal, statusLabels, type LifeGoal, type Category } from '../api/goals'
import { errorMessage } from '../api/http'
import { readChecks, readRecords, readAttachments, readCover, write, upload, downloadFile, fileSize,
  type CheckItem, type GoalRecord, type Attachment } from '../api/details'
import { formatSlot } from '../goals/slots'
import { readCompletion, undoCompletion, type Completion } from '../api/completion'
import CompletionDialog from '../components/CompletionDialog.vue'
import CompletionFeedback from '../components/CompletionFeedback.vue'
import ModalDialog from '../components/ModalDialog.vue'
import AttachmentImage from '../components/AttachmentImage.vue'
import '../styles/detail.css'

const route = useRoute()
const router = useRouter()
const goal = ref<LifeGoal>()
const completion = ref<Completion>()
const completionOpen = ref(false)
const feedback = ref<number>()
const proofs = computed(() => attachments.value.filter(file => file.stage === 'COMPLETION'))
async function completed() {
  if (goal.value?.status !== 'COMPLETED') feedback.value = goal.value!.slotNo
  completionOpen.value = false
  await run(refresh)
}
function undo() {
  confirmation.value = { title: '撤销完成？', message: '恢复完成前的状态，完成档案与证明附件都会保留。', action: async () => {
    await undoCompletion(goal.value!.slotNo); confirmation.value = undefined; await refresh()
  } }
}
const categories = ref<Category[]>([])
const checks = ref<CheckItem[]>([])
const records = ref<GoalRecord[]>([])
const attachments = ref<Attachment[]>([])
const cover = ref<Attachment>()
const error = ref('')
const loading = ref(true)
const busy = ref(false)
const editing = ref(false)
const more = ref(false)
const preview = ref<Attachment>()
const form = reactive({ title: '', categoryId: '', status: 'NOT_STARTED', reason: '' })
const checkForm = ref<{ id?: number; content: string; completed: boolean }>()
const recordForm = ref<{ id?: number; content: string; recordDate: string }>()
const confirmation = ref<{ title: string; message: string; action: () => Promise<void> }>()
const categoryName = computed(() => categories.value.find(c => c.id === goal.value?.categoryId)?.name || '不分类')
let revision = 0
async function load() {
  const current = ++revision
  loading.value = true; error.value = ''; editing.value = false; more.value = false
  goal.value = undefined; completion.value = undefined; completionOpen.value = false
  checkForm.value = undefined; recordForm.value = undefined; confirmation.value = undefined; preview.value = undefined
  const slot = Number(route.params.slotNo)
  if (!Number.isInteger(slot) || slot < 1 || slot > 1000) {
    error.value = '编号必须为 001～1000。'; loading.value = false; return
  }
  try {
    const [value, cats, items, notes, files, image, archive] = await Promise.all([
      getGoal(slot), getCategories(), readChecks(slot), readRecords(slot), readAttachments(slot), readCover(slot), readCompletion(slot),
    ])
    if (current !== revision) return
    goal.value = value; categories.value = cats; checks.value = items; records.value = notes
    attachments.value = files; cover.value = image; completion.value = archive
  } catch (cause) { if (current === revision) error.value = errorMessage(cause) }
  finally { if (current === revision) loading.value = false }
}
async function refresh() {
  if (!goal.value) return
  const slot = goal.value.slotNo
  const current = revision
  const [value, items, notes, files, image, archive] = await Promise.all([
    getGoal(slot), readChecks(slot), readRecords(slot), readAttachments(slot), readCover(slot), readCompletion(slot),
  ])
  if (current !== revision) return
  goal.value = value; checks.value = items; records.value = notes; attachments.value = files; cover.value = image; completion.value = archive
}
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await action() } catch (cause) { error.value = errorMessage(cause) }
  finally { busy.value = false }
}
function edit() {
  if (!goal.value) return
  Object.assign(form, { title: goal.value.title, categoryId: goal.value.categoryId?.toString() || '',
    status: goal.value.status, reason: goal.value.reason || '' })
  editing.value = true
}
async function saveGoal() {
  if (!form.title.trim()) { error.value = '请先写下标题。'; return }
  const current = revision
  const original = goal.value!
  await run(async () => {
    const updated = await write<LifeGoal>(`/goals/${original.slotNo}`, 'PUT', {
      title: form.title.trim(), categoryId: form.categoryId ? Number(form.categoryId) : null,
      status: original.status === 'COMPLETED' ? null : form.status, reason: form.reason || null,
    })
    if (current === revision) { goal.value = updated; editing.value = false }
  })
}
async function saveCheck() {
  const value = checkForm.value
  if (!value?.content.trim()) { error.value = '请写下完成条件。'; return }
  await run(async () => {
    await write(value.id ? `/check-items/${value.id}` : `/goals/${goal.value!.slotNo}/check-items`,
      value.id ? 'PUT' : 'POST', { content: value.content.trim(), completed: value.completed })
    checkForm.value = undefined; await refresh()
  })
}
function toggleCheck(item: CheckItem, event: Event) {
  const input = event.target as HTMLInputElement
  void run(async () => {
    await write(`/check-items/${item.id}`, 'PUT', { content: item.content, completed: !item.completed })
    await refresh()
  }).finally(() => { input.checked = checks.value.find(value => value.id === item.id)?.completed ?? item.completed })
}
function newRecord() {
  const now = new Date()
  const date = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  recordForm.value = { content: '', recordDate: date }
}
async function saveRecord() {
  const value = recordForm.value
  if (!value?.content.trim()) { error.value = '请写下记录正文。'; return }
  await run(async () => {
    await write(value.id ? `/records/${value.id}` : `/goals/${goal.value!.slotNo}/records`,
      value.id ? 'PUT' : 'POST', { content: value.content, recordDate: value.recordDate })
    recordForm.value = undefined; await refresh()
  })
}
function confirmDelete(title: string, message: string, path: string) {
  confirmation.value = { title, message, action: async () => {
    await write(path, 'DELETE'); confirmation.value = undefined; await refresh()
  } }
}
function clearGoal() {
  confirmation.value = { title: `清空第 ${formatSlot(goal.value!.slotNo)} 件？`,
    message: '删除事项将同时删除对应记录和附件。这个编号将重新成为空白，其他编号保持原样。',
    action: async () => { await deleteGoal(goal.value!.slotNo); confirmation.value = undefined; await router.push('/goals') } }
}
async function uploadFiles(event: Event, recordId?: number) {
  const input = event.target as HTMLInputElement
  const files = [...(input.files || [])]
  const slot = goal.value!.slotNo
  input.value = ''
  await run(async () => {
    try {
      for (const file of files) {
        if (file.size > 50 * 1024 * 1024) throw new Error('too-large')
        await upload(slot, file, recordId)
      }
    } catch (cause) {
      if (cause instanceof Error && cause.message === 'too-large') {
        error.value = '文件不能超过 50 MB。已上传的文件仍会保留。'
      } else { error.value = errorMessage(cause) }
    }
    await refresh()
  })
}
const recordFiles = (id: number) => attachments.value.filter(file => file.recordId === id)
function setCover(file: Attachment) {
  void run(async () => { await write(`/goals/${goal.value!.slotNo}/cover/${file.id}`, 'PUT'); await refresh() })
}
function setBackground(file: Attachment, event: Event) {
  const input = event.target as HTMLInputElement
  void run(async () => {
    await write(`/attachments/${file.id}/home-background`, 'PUT', { allowed: !file.allowHomeBackground }); await refresh()
  }).finally(() => { input.checked = attachments.value.find(value => value.id === file.id)?.allowHomeBackground ?? file.allowHomeBackground })
}
watch(() => route.params.slotNo, load, { immediate: true })
</script>

<template>
  <article class="paper goal-detail">
    <nav class="detail-toolbar">
      <RouterLink to="/goals">← 返回人生千事</RouterLink>
      <div v-if="goal" class="detail-actions">
        <button v-if="!editing" :disabled="busy" @click="edit">编辑</button>
        <button :aria-expanded="more" @click="more = !more">更多</button>
        <button v-if="more && goal.status === 'COMPLETED'" :disabled="busy" @click="undo">撤销完成</button>
        <button v-if="more" :disabled="busy" @click="clearGoal">清空这个编号</button>
      </div>
    </nav>
    <p v-if="error && !checkForm && !recordForm && !confirmation" class="form-error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">正在翻阅…</p>
    <button v-else-if="!goal" @click="load">重新读取</button>
    <template v-else>
      <header class="detail-heading">
        <p class="eyebrow">第 {{ formatSlot(goal.slotNo) }} 件</p>
        <h1>{{ goal.title }}</h1>
        <p class="detail-meta">{{ categoryName }} · {{ statusLabels[goal.status] }}</p>
      </header>
      <button v-if="cover" class="detail-banner" aria-label="预览事项封面" @click="preview = cover">
        <AttachmentImage :id="cover.id" :alt="goal.title" />
      </button>
      <div v-else class="detail-banner no-image"><span>✦</span>尚无影像</div>

      <form v-if="editing" class="entry-form basic-edit" @submit.prevent="saveGoal">
        <h2>编辑这一件事</h2>
        <label>标题<input v-model="form.title" required maxlength="255" autofocus /></label>
        <div class="edit-columns">
          <label>分类<select v-model="form.categoryId" aria-label="分类"><option value="">不分类</option><option v-for="c in categories" :key="c.id" :value="String(c.id)">{{ c.name }}</option></select></label>
          <label v-if="goal.status !== 'COMPLETED'">状态<select v-model="form.status" aria-label="状态"><option value="NOT_STARTED">未开始</option><option value="IN_PROGRESS">进行中</option></select></label>
          <p v-else>已完成</p>
        </div>
        <label>为什么想做<textarea v-model="form.reason" rows="7" /></label>
        <div class="dialog-actions"><button type="button" :disabled="busy" @click="editing = false">取消</button><button class="ink-button" :disabled="busy">保存修改</button></div>
      </form>
      <section v-else class="detail-section reason-section">
        <h2>为什么想做</h2>
        <p v-if="goal.reason" class="prose">{{ goal.reason }}</p>
        <p v-else class="quiet">还没有写下缘由。</p>
      </section>

      <section class="detail-section">
        <div class="section-heading"><h2>完成条件 <small>可选</small></h2><button :disabled="busy" @click="checkForm = { content: '', completed: false }">新增条件</button></div>
        <ul class="check-list">
          <li v-for="item in checks" :key="item.id">
            <label class="check-content"><input type="checkbox" :checked="item.completed" :disabled="busy" @change="toggleCheck(item, $event)" /><span :class="{ checked: item.completed }">{{ item.content }}</span></label>
            <div class="row-actions"><button :disabled="busy" :aria-label="'修改条件：' + item.content" @click="checkForm = { ...item }">修改</button><button :disabled="busy" :aria-label="'删除条件：' + item.content" @click="confirmDelete('删除完成条件？', item.content, '/check-items/' + item.id)">删除</button></div>
          </li>
        </ul>
      </section>

      <section class="detail-section">
        <div class="section-heading"><h2>我的记录</h2><button :disabled="busy" @click="newRecord">写一条记录</button></div>
        <p v-if="!records.length" class="quiet">从一个念头、一段经历开始记起。</p>
        <ol class="record-list">
          <li v-for="record in records" :key="record.id" :data-record="record.id">
            <div class="section-heading"><time :datetime="record.recordDate">{{ record.recordDate.replaceAll('-', '.') }}</time>
              <div class="row-actions"><button :disabled="busy" @click="recordForm = { ...record }">编辑记录</button><button :disabled="busy" @click="confirmDelete('删除这条记录？', '这条记录及其附件将一并删除。', '/records/' + record.id)">删除记录</button></div>
            </div>
            <p class="prose">{{ record.content }}</p>
            <div class="record-files">
              <button v-for="file in recordFiles(record.id)" :key="file.id" :disabled="busy" @click="file.isImage ? preview = file : run(() => downloadFile(file))">
                <AttachmentImage v-if="file.isImage" :id="file.id" :alt="file.originalName" />{{ file.originalName }}
              </button>
            </div>
            <label class="upload-button">添加记录附件<input type="file" multiple :disabled="busy" :aria-label="'添加记录附件 ' + record.recordDate" @change="uploadFiles($event, record.id)" /></label>
          </li>
        </ol>
      </section>

      <section class="detail-section">
        <div class="section-heading"><h2>全部附件</h2><label class="upload-button">上传附件<input type="file" multiple :disabled="busy" aria-label="上传普通附件" @change="uploadFiles($event)" /></label></div>
        <p class="quiet">图片与文档，都可以留在这里。每个文件最多 50 MB。</p>
        <p v-if="busy" role="status">正在保存，请稍候…</p>
        <div class="attachment-grid">
          <article v-for="file in attachments" :key="file.id" class="attachment-card" :data-attachment="file.id">
            <button v-if="file.isImage" class="attachment-thumb" :aria-label="'预览 ' + file.originalName" @click="preview = file"><AttachmentImage :id="file.id" :alt="file.originalName" /></button>
            <div v-else class="document-symbol" aria-hidden="true">▤</div>
            <h3>{{ file.originalName }}</h3>
            <p class="quiet">{{ file.originalName.split('.').pop()?.toUpperCase() }} · {{ fileSize(file.fileSize) }} · {{ file.stage === 'PROCESS' ? '过程记录' : file.stage === 'COMPLETION' ? '完成证明' : '事项附件' }}</p>
            <div class="row-actions"><button :disabled="busy" @click="run(() => downloadFile(file))">下载</button><button :disabled="busy" @click="confirmDelete('删除附件？', file.originalName + ' 将从记录册与磁盘中删除。', '/attachments/' + file.id)">删除附件</button></div>
            <template v-if="file.isImage">
              <button :disabled="busy || goal.coverAttachmentId === file.id" @click="setCover(file)">{{ goal.coverAttachmentId === file.id ? '已选为封面' : '设为封面' }}</button>
              <label class="background-option"><input type="checkbox" :checked="file.allowHomeBackground" :disabled="busy" @change="setBackground(file, $event)" />允许作为首页背景</label>
            </template>
          </article>
        </div>
      </section>
      <section v-if="goal.status === 'COMPLETED'" class="detail-section completion-section">
        <div class="section-heading"><h2>完成之后</h2><button :disabled="busy" @click="completionOpen = true">编辑完成档案</button></div>
        <template v-if="completion">
          <p>完成日期：<time :datetime="completion.completedDate">{{ completion.completedDate.replaceAll('-', '.') }}</time></p>
          <p v-if="completion.completionNote" class="prose">{{ completion.completionNote }}</p>
          <p v-if="completion.rating" class="archive-rating" :aria-label="completion.rating + ' 星'">{{ '★'.repeat(completion.rating) }}</p>
        </template>
        <div v-if="proofs.length" class="record-files">
          <button v-for="file in proofs" :key="file.id" @click="file.isImage ? preview = file : run(() => downloadFile(file))">
            <AttachmentImage v-if="file.isImage" :id="file.id" :alt="file.originalName" />{{ file.originalName }}
          </button>
        </div>
      </section>
      <div v-else class="detail-section completion-entry"><button :disabled="busy" @click="completionOpen = true">○ 标记为完成</button></div>
    </template>
    <CompletionDialog v-if="completionOpen && goal" :goal="goal" :editing="goal.status === 'COMPLETED'" @close="completionOpen = false" @saved="completed" />
    <CompletionFeedback v-if="feedback !== undefined" :slot="feedback" @expired="feedback = undefined" />

    <ModalDialog v-if="checkForm" title="完成条件" :busy="busy" @close="checkForm = undefined">
      <form class="entry-form" @submit.prevent="saveCheck"><label>条件内容<textarea v-model="checkForm.content" required maxlength="10000" rows="3" autofocus /></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p><div class="dialog-actions"><button type="button" :disabled="busy" @click="checkForm = undefined">取消</button><button class="ink-button" :disabled="busy">保存条件</button></div>
      </form>
    </ModalDialog>
    <ModalDialog v-if="recordForm" title="我的记录" :busy="busy" @close="recordForm = undefined">
      <form class="entry-form" @submit.prevent="saveRecord"><label>记录日期<input v-model="recordForm.recordDate" type="date" required /></label><label>记录正文<textarea v-model="recordForm.content" rows="7" required autofocus /></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p><div class="dialog-actions"><button type="button" :disabled="busy" @click="recordForm = undefined">取消</button><button class="ink-button" :disabled="busy">保存记录</button></div>
      </form>
    </ModalDialog>
    <ModalDialog v-if="confirmation" :title="confirmation.title" :busy="busy" @close="confirmation = undefined">
      <p>{{ confirmation.message }}</p><p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <div class="dialog-actions"><button autofocus :disabled="busy" @click="confirmation = undefined">取消</button><button class="ink-button" :disabled="busy" @click="run(confirmation.action)">{{ confirmation.title.startsWith('清空') ? '确认清空' : confirmation.title.startsWith('撤销') ? '确认撤销' : '确认删除' }}</button></div>
    </ModalDialog>
    <ModalDialog v-if="preview" class="image-dialog" :title="preview.originalName" @close="preview = undefined">
      <AttachmentImage :id="preview.id" :alt="preview.originalName" /><div class="dialog-actions"><button autofocus @click="preview = undefined">关闭预览</button></div>
    </ModalDialog>
  </article>
</template>
