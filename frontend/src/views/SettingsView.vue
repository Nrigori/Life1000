<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { readSettings,readImages,updateSettings,exportBackup,type Settings,type BackgroundImage } from '../api/settings'
import { getCategories,type Category } from '../api/goals'
import { write,fileSize } from '../api/details'
import { errorMessage } from '../api/http'
import { formatSlot } from '../goals/slots'
import AttachmentImage from '../components/AttachmentImage.vue'
import ModalDialog from '../components/ModalDialog.vue'
const settings=ref<Settings>()
const images=ref<BackgroundImage[]>([])
const categories=ref<Category[]>([])
const loading=ref(true),busy=ref(false),exporting=ref(false)
const error=ref(''),formError=ref(''),notice=ref('')
const form=ref<{id?:number;name:string;sortOrder:number}>()
const deleting=ref<Category>()
const visible=ref(12)
const controller=new AbortController()
const exportController=new AbortController()
const fixed=computed(()=>settings.value?.fixedImage)
async function load() {
  if(busy.value) return
  loading.value=true;error.value=''
  try {
    const [s,i,c]=await Promise.all([readSettings(controller.signal),readImages(controller.signal),getCategories(controller.signal)])
    settings.value=s;images.value=i;categories.value=c
  } catch(cause){if(!controller.signal.aborted) error.value=errorMessage(cause)}
  finally {loading.value=false}
}
async function change(values:Record<string,string|null>) {
  if(busy.value) return
  busy.value=true;error.value='';notice.value=''
  try {settings.value=await updateSettings(values);notice.value='已保存。'}
  catch(cause){error.value=errorMessage(cause)}
  finally{busy.value=false}
}
async function mode(event:Event,value:string) {
  const fieldset=(event.target as HTMLInputElement).closest("fieldset")!
  await change({HOME_BACKGROUND_MODE:value})
  fieldset.querySelectorAll<HTMLInputElement>("input").forEach(input=>{input.checked=input.value===settings.value?.mode})
}
async function candidate(image:BackgroundImage,event:Event) {
  const input=event.target as HTMLInputElement
  if(busy.value) return
  busy.value=true;error.value=''
  try {await write('/attachments/'+image.attachmentId+'/home-background','PUT',{allowed:input.checked});image.allowHomeBackground=input.checked}
  catch(cause){input.checked=image.allowHomeBackground;error.value=errorMessage(cause)}
  finally{busy.value=false}
}
function edit(value?:Category) {
  formError.value=''
  form.value=value?{...value}:{name:'',sortOrder:0}
}
async function saveCategory(){
  if(!form.value||busy.value) return
  if(!form.value.name.trim()){formError.value='请填写分类名称。';return}
  if(!Number.isInteger(form.value.sortOrder)||form.value.sortOrder < -2147483648||form.value.sortOrder>2147483647){formError.value='排序须为有效整数。';return}
  busy.value=true;formError.value=''
  try {
    const value=form.value
    await write(value.id?'/categories/'+value.id:'/categories',value.id?'PUT':'POST',{name:value.name.trim(),sortOrder:value.sortOrder})
    categories.value=await getCategories();form.value=undefined
  }catch(cause){formError.value=errorMessage(cause)}
  finally{busy.value=false}
}
async function removeCategory(){
  if(!deleting.value||busy.value) return
  busy.value=true;formError.value=''
  try{await write('/categories/'+deleting.value.id,'DELETE');categories.value=await getCategories();deleting.value=undefined}
  catch(cause){formError.value=errorMessage(cause)}
  finally{busy.value=false}
}
async function backup(){
  if(exporting.value) return
  exporting.value=true;error.value='';notice.value=''
  try{await exportBackup(exportController.signal);notice.value='备份已交给浏览器下载。请查看 backup-info.json 中的缺失文件说明。'}
  catch(cause){if(!exportController.signal.aborted)error.value=errorMessage(cause)}
  finally{exporting.value=false}
}
onMounted(load)
onBeforeUnmount(()=>{controller.abort();exportController.abort()})
</script>
<template>
  <section class="settings-page">
    <h1>设置</h1>
    <p v-if="loading" role="status" class="quiet">正在翻阅设置…</p>
    <div v-if="error" class="form-error" role="alert">{{error}} <button :disabled="loading||busy" @click="load">重新读取</button></div>
    <p v-if="notice" class="quiet" role="status">{{notice}}</p>
    <p v-if="busy" class="quiet" role="status">正在保存，请稍候…</p>
    <template v-if="settings">
      <section class="paper settings-section" aria-labelledby="home-settings-title">
        <h2 id="home-settings-title">首页</h2>
        <fieldset class="background-modes" :disabled="busy||loading">
          <legend>首页背景</legend>
          <label><input type="radio" name="background-mode" :checked="settings.mode==='RANDOM'" value="RANDOM" @change="mode($event,'RANDOM')" />随机背景</label>
          <label><input type="radio" name="background-mode" :checked="settings.mode==='FIXED'" value="FIXED" @change="mode($event,'FIXED')" />固定背景</label>
        </fieldset>
        <p class="quiet">每次进入首页重新选择，不自动轮播。固定背景从已有图片中选择。</p>
        <div v-if="fixed" class="fixed-preview">
          <AttachmentImage :id="fixed.attachmentId" :alt="'当前固定背景：'+fixed.originalName" />
          <p>当前固定背景<br /><strong>{{fixed.originalName}}</strong><br />第 {{formatSlot(fixed.slotNo)}} 件</p>
        </div>
        <p v-else class="quiet">固定背景未选择或已经失效；固定模式下将使用纸张默认背景。</p>
        <h3>随机背景候选</h3>
        <p v-if="!images.length" class="quiet">还没有图片。可以先在人生事项中留下影像。</p>
        <div class="background-grid">
          <article v-for="image in images.slice(0,visible)" :key="image.attachmentId" class="background-candidate" :data-image="image.attachmentId">
            <div class="candidate-preview"><AttachmentImage :id="image.attachmentId" :alt="image.originalName" /></div>
            <p class="candidate-name">{{image.originalName}}</p>
            <RouterLink :to="'/goals/'+image.slotNo">第 {{formatSlot(image.slotNo)}} 件</RouterLink>
            <label><input type="checkbox" :checked="image.allowHomeBackground" :disabled="busy||loading" @change="candidate(image,$event)" />允许作为首页背景</label>
            <button :disabled="busy||loading||fixed?.attachmentId===image.attachmentId" @click="change({HOME_FIXED_BACKGROUND_PATH:image.filePath})">{{fixed?.attachmentId===image.attachmentId?'已选为固定背景':'设为固定首页背景'}}</button>
          </article>
        </div>
        <button v-if="visible<images.length" class="more-images" @click="visible+=12">继续查看图片（{{visible}} / {{images.length}}）</button>
      </section>
      <section class="paper settings-section" aria-labelledby="category-settings-title">
        <div class="settings-heading"><h2 id="category-settings-title">分类</h2><button :disabled="busy||loading" @click="edit()">＋ 新增分类</button></div>
        <p class="quiet">直接编辑排序数字；数字相同时保持原有顺序。</p>
        <p v-if="!categories.length" class="quiet">还没有分类。事项也可以保持“不分类”。</p>
        <ol class="category-list">
          <li v-for="category in categories" :key="category.id" :data-category="category.id">
            <span>{{category.name}} <small>排序 {{category.sortOrder}}</small></span>
            <div><button :disabled="busy||loading" @click="edit(category)">编辑</button><button :disabled="busy||loading" @click="deleting=category;formError=''">删除</button></div>
          </li>
        </ol>
      </section>
      <section class="paper settings-section" aria-labelledby="data-settings-title">
        <h2 id="data-settings-title">数据</h2><h3>数据备份</h3>
        <p class="quiet">导出完整文字、设置与附件。V1 只提供导出，不支持导入恢复。</p>
        <button :disabled="exporting||loading||busy" @click="backup">{{exporting?'正在整理备份…':'导出完整备份'}}</button>
        <p v-if="exporting" role="status" class="quiet">文件较多时需要一些时间，请保持页面打开。</p>
      </section>
      <section class="paper settings-section" aria-labelledby="file-settings-title">
        <h2 id="file-settings-title">文件</h2>
        <dl class="file-usage">
          <div><dt>图片</dt><dd>{{settings.files.imageCount}} 张</dd></div>
          <div><dt>文档</dt><dd>{{settings.files.documentCount}} 个</dd></div>
          <div><dt>占用</dt><dd>{{fileSize(settings.files.totalBytes)}}</dd></div>
        </dl>
        <p class="quiet">按已保存的附件信息统计。</p>
      </section>
    </template>
    <ModalDialog v-if="form" :title="form.id?'编辑分类':'新增分类'" :busy="busy" @close="form=undefined">
      <form class="entry-form" @submit.prevent="saveCategory">
        <label>名称<input v-model="form.name" required maxlength="100" autofocus :disabled="busy" /></label>
        <label>排序<input v-model.number="form.sortOrder" type="number" step="1" min="-2147483648" max="2147483647" required :disabled="busy" /></label>
        <p v-if="formError" role="alert" class="form-error">{{formError}}</p>
        <div class="dialog-actions"><button type="button" :disabled="busy" @click="form=undefined">取消</button><button class="ink-button" :disabled="busy">保存</button></div>
      </form>
    </ModalDialog>
    <ModalDialog v-if="deleting" title="删除分类？" :busy="busy" @close="deleting=undefined">
      <p>删除“{{deleting.name}}”不会删除人生事项，相关事项将变为“不分类”。</p>
      <p v-if="formError" role="alert" class="form-error">{{formError}}</p>
      <div class="dialog-actions"><button autofocus :disabled="busy" @click="deleting=undefined">取消</button><button class="ink-button" :disabled="busy" @click="removeCategory">确认删除</button></div>
    </ModalDialog>
  </section>
</template>
<style scoped>
.settings-page{max-width:1000px;margin:auto}
.settings-section{min-height:0;padding:36px 40px;margin-top:26px}
h2{font:26px Georgia,"SimSun",serif;margin:0 0 24px}
h3{font-size:16px;font-weight:400;margin:28px 0 16px}
.quiet{color:var(--muted);font-size:13px;line-height:1.8}
.settings-heading{display:flex;justify-content:space-between;align-items:baseline;gap:20px}
.background-modes{border:0;padding:0;margin:0;display:flex;gap:28px}
legend{margin-bottom:16px;font-size:14px}
label{font-size:13px}
input[type=radio],input[type=checkbox]{width:auto;margin-right:8px;accent-color:var(--accent)}
.fixed-preview{display:flex;align-items:center;gap:24px;font-size:13px;line-height:1.9;margin-top:20px}
.fixed-preview img{width:160px;height:90px;object-fit:cover;border-radius:4px}
.fixed-preview p{overflow-wrap:anywhere;min-width:0}.fixed-preview strong{font-weight:400}
.background-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:24px}
.background-candidate{min-width:0;display:flex;flex-direction:column;gap:12px}
.candidate-preview{height:145px;background:#e9decd;display:grid;place-items:center;border-radius:4px;overflow:hidden;font-size:12px;color:var(--muted)}
.candidate-preview img{width:100%;height:100%;object-fit:cover}
.candidate-name{margin:0;overflow-wrap:anywhere;font-size:14px}
.background-candidate a{font-size:12px;color:var(--muted)}.background-candidate a:hover{text-decoration:underline}
.background-candidate button{font-size:12px;margin-top:auto}
.more-images{display:block;margin:26px auto 0}
.category-list{list-style:none;padding:0;margin:0}
.category-list li{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:16px 0;border-bottom:1px solid var(--line)}
.category-list span{overflow-wrap:anywhere;min-width:0}.category-list small{display:block;color:var(--muted);font-size:12px;margin-top:6px}
.category-list li>div{display:flex;flex-shrink:0;gap:8px}
.category-list button{font-size:13px;padding:7px 12px}
.file-usage{display:flex;gap:70px}.file-usage div{display:grid;gap:12px}.file-usage dt{color:var(--muted);font-size:13px}.file-usage dd{margin:0;font:24px Georgia,serif}
@media(max-width:800px){.background-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.settings-section{padding:28px 24px}.file-usage{gap:30px}}
@media(max-width:460px){.background-grid{grid-template-columns:1fr}.fixed-preview{align-items:flex-start;flex-direction:column}.file-usage{flex-wrap:wrap}}
</style>
