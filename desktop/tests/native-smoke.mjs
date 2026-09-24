// 真实 Windows WebView2 / Spring / MySQL 冒烟测试；不是模拟 API。
// 只接受 life1000_test 和仓库 .cache 内的附件目录，不清空数据库。
import { createRequire } from 'node:module'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { spawn, execFileSync } from 'node:child_process'
import { resolve, dirname, join, relative, isAbsolute } from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'
import assert from 'node:assert/strict'
import net from 'node:net'
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const require = createRequire(join(root, 'frontend/package.json'))
const { chromium, expect } = require('@playwright/test')
const configPath = resolve(process.argv[2] || '')
assert(process.argv[2], 'Provide a private test config path')
const config = JSON.parse((await readFile(configPath, 'utf8')).replace(/^\uFEFF/, ''))
assert(/^jdbc:mysql:\/\/[^/]+\/life1000_test(?:\?|$)/.test(config.DB_URL), 'Requires life1000_test')
const uploadRelative = relative(join(root, '.cache'), resolve(config.LIFE1000_UPLOAD_DIRECTORY))
assert(uploadRelative && !isAbsolute(uploadRelative) && !uploadRelative.startsWith('..') && !resolve(config.LIFE1000_UPLOAD_DIRECTORY).includes('..'), 'Test uploads must be in repository .cache')
assert.equal(execFileSync('pwsh.exe', ['-NoProfile','-Command', "(Get-Process -Name Life1000 -ErrorAction SilentlyContinue | Measure-Object).Count"], {encoding:'utf8',windowsHide:true}).trim(), '0', 'Close existing Desktop before smoke testing')
for (const port of [8080, 9229]) {
  await new Promise((resolve, reject) => {
    const server = net.createServer()
    server.once('error', reject)
    server.listen(port, '127.0.0.1', () => server.close(resolve))
  })
}
const exe = join(root,'desktop/src-tauri/target/release/Life1000.exe')
const environment = {...process.env, LIFE1000_DESKTOP_CONFIG:configPath,
  WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS:'--remote-debugging-port=9229 --remote-debugging-address=127.0.0.1'}
let app, browser, page, slot, categoryId, originalSettings, createdTitle
const nativeErrors=[]
async function open() {
  app=spawn(exe,[],{env:environment,stdio:['ignore','ignore','pipe'],windowsHide:false})
  app.stderr.on('data',data=>console.error(data.toString()))
  app.on('exit',code=>console.log('Desktop exit code:',code))
  let endpoint=false
  for(let attempt=0;attempt<60;attempt++) {
    if(app.exitCode!==null) throw Error('Desktop exited before WebView became available')
    try { const response=await fetch('http://127.0.0.1:9229/json/version'); if(response.ok){endpoint=true;break} } catch {}
    await delay(500)
  }
  assert(endpoint,'WebView2 CDP did not start')
  browser=await chromium.connectOverCDP('http://127.0.0.1:9229')
  await expect.poll(()=>browser.contexts()[0]?.pages().length || 0,{timeout:15000}).toBeGreaterThan(0)
  page=browser.contexts()[0].pages()[0]
  page.setDefaultTimeout(20000)
  page.on('pageerror',error=>nativeErrors.push(error.message))
  await Promise.race([
    expect(page.getByRole('button',{name:'进入',exact:true})).toBeVisible({timeout:140000}),
    page.locator('.desktop-startup button').waitFor({state:'visible',timeout:140000}).then(async()=>{throw Error(await page.locator('.desktop-startup p').innerText())})
  ])
  assert(page.url().startsWith('http://tauri.localhost/'))
  console.log('PASS native window + HTTP ready + embedded production frontend')
}
async function login(){
  await page.getByLabel('账号',{exact:true}).fill(config.LIFE1000_USERNAME)
  await page.getByLabel('密码',{exact:true}).fill(config.LIFE1000_PASSWORD)
  await page.getByRole('button',{name:'进入',exact:true}).click()
  await expect(page.locator('.home-cover')).toBeVisible()
  console.log('PASS native login with real JWT')
}
async function api(path,method='GET',body){
  return page.evaluate(async({path,method,body})=>{
    const response=await fetch('http://127.0.0.1:8080/api'+path,{method,
      headers:{Authorization:'Bearer '+sessionStorage.getItem('life1000.accessToken'),...(body===undefined?{}:{'Content-Type':'application/json'})},
      ...(body===undefined?{}:{body:JSON.stringify(body)})})
    if(!response.ok) throw Error('API '+method+' '+path+' returned '+response.status)
    return response.status===204?null:response.json()
  },{path,method,body})
}
async function close(){
  if(!app || app.exitCode!==null) return
  execFileSync('pwsh.exe',['-NoProfile','-Command',`$p=Get-Process -Id ${app.pid} -ErrorAction Stop; $null=$p.CloseMainWindow()`],{windowsHide:true})
  for(let i=0;i<40 && app.exitCode===null;i++) await delay(250)
  assert.notEqual(app.exitCode,null,'Desktop failed to exit after close')
  browser=undefined
  await expect.poll(async()=>{try{await fetch('http://127.0.0.1:8080/api/health',{signal:AbortSignal.timeout(500)});return false}catch{return true}},{timeout:10000}).toBe(true)
  console.log('PASS close window stops owned backend')
}
function pdf(){
  const stream='BT /F1 18 Tf 30 200 Td (Life1000 PDF preview) Tj ET\n'
  const objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>',
    `<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}endstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>']
  let content='%PDF-1.4\n',offsets=[0]
  objects.forEach((value,index)=>{offsets.push(Buffer.byteLength(content));content+=`${index+1} 0 obj\n${value}\nendobj\n`})
  const start=Buffer.byteLength(content),size=objects.length+1
  content+=`xref\n0 ${size}\n0000000000 65535 f \n`+offsets.slice(1).map(x=>String(x).padStart(10,'0')+' 00000 n \n').join('')
  content+=`trailer\n<< /Size ${size} /Root 1 0 R >>\nstartxref\n${start}\n%%EOF\n`
  return Buffer.from(content)
}
try {
  await open()
  await login()
  const second=spawn(exe,[],{env:environment,stdio:'ignore',windowsHide:false})
  await expect.poll(()=>second.exitCode,{timeout:10000}).not.toBeNull()
  assert.equal(app.exitCode,null)
  console.log('PASS second instance does not start another backend')
  originalSettings=await api('/settings')
  const name='Desktop smoke '+Date.now()
  await page.goto('http://tauri.localhost/settings')
  await page.getByRole('button',{name:'＋ 新增分类',exact:true}).click()
  await page.getByRole('dialog').getByLabel('名称',{exact:true}).fill(name)
  await page.getByRole('dialog').getByRole('button',{name:'保存',exact:true}).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  categoryId=(await api('/categories')).find(c=>c.name===name)?.id
  assert(categoryId)
  console.log('PASS category created by desktop UI')
  const goals=await api('/goals/range?fromSlot=1&toSlot=1000')
  slot=Array.from({length:1000},(_,i)=>i+1).find(n=>!goals.some(g=>g.slotNo===n))
  assert(slot,'No blank test slot; test never clears existing data')
  await page.goto('http://tauri.localhost/goals')
  const tile=page.locator(`[data-slot="${slot}"]`)
  while(!await tile.count()) await page.getByRole('button',{name:'继续向下展开'}).click()
  await tile.getByRole('button').first().click()
  await page.getByRole('dialog').getByLabel(/^标题/).fill(name)
  await page.getByRole('dialog').getByRole('button',{name:'写下它',exact:true}).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  createdTitle=name
  await page.goto('http://tauri.localhost/goals/'+slot)
  await page.getByRole('button',{name:'编辑',exact:true}).click()
  await page.getByLabel('标题',{exact:true}).fill(name+' edited')
  await page.getByLabel('分类',{exact:true}).selectOption(String(categoryId))
  await page.getByLabel('为什么想做',{exact:true}).fill('Native WebView2 test record')
  await page.getByRole('button',{name:'保存修改',exact:true}).click()
  await expect(page.getByRole('heading',{name:name+' edited',exact:true})).toBeVisible()
  createdTitle=name+' edited'
  console.log('PASS goal create/edit with stable slot')
  const inputs=[
    {name:'desktop.png',mimeType:'image/png',buffer:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j6X8AAAAASUVORK5CYII=','base64')},
    {name:'desktop.md',mimeType:'text/markdown',buffer:Buffer.from('# Desktop markdown\n\n**Preview**')},
    {name:'desktop.txt',mimeType:'text/plain',buffer:Buffer.from('Desktop text\n第二行')},
    {name:'desktop.pdf',mimeType:'application/pdf',buffer:pdf()}
  ]
  await page.getByLabel('上传普通附件',{exact:true}).setInputFiles(inputs)
  await expect(page.locator('.attachment-card')).toHaveCount(4)
  await page.reload()
  await expect(page.locator('.attachment-card')).toHaveCount(4)
  for(const input of inputs){
    const card=page.locator('.attachment-card').filter({has:page.getByRole('heading',{name:input.name,exact:true})})
    await card.getByRole('button',{name:'预览',exact:true}).click()
    const dialog=page.getByRole('dialog')
    if(input.name.endsWith('.md')) await expect(dialog.getByRole('heading',{name:'Desktop markdown'})).toBeVisible()
    else if(input.name.endsWith('.txt')) await expect(dialog.locator('pre')).toContainText('第二行')
    else if(input.name.endsWith('.png')) await expect.poll(()=>dialog.locator('img').evaluate(e=>e.naturalWidth)).toBeGreaterThan(0)
    else {
      // WebView2 的原生 PDF 绘制层可能不出现在 CDP 截图中；这里只验证认证 Blob 已装载。
      await expect(dialog.locator('iframe')).toHaveAttribute('src',/^blob:/)
      await delay(1000)
    }
    await page.keyboard.press('Escape')
    await expect(dialog).toHaveCount(0)
  }
  console.log('PASS uploads persist + image/Markdown/TXT preview + PDF Blob iframe')
  const files=await api('/goals/'+slot+'/attachments')
  await api('/attachments/'+files.find(f=>f.isImage).id+'/home-background','PUT',{allowed:true})
  await page.getByRole('button',{name:'○ 标记为完成',exact:true}).click()
  await page.getByLabel('完成感想',{exact:true}).fill('Desktop completion')
  await page.getByRole('button',{name:'确认完成',exact:true}).click()
  await expect(page.getByRole('heading',{name:'完成之后',exact:true})).toBeVisible()
  await page.goto('http://tauri.localhost/timeline')
  await page.getByRole('link').filter({hasText:name+' edited'}).click()
  await expect.poll(()=>new URL(page.url()).pathname).toBe('/goals/'+slot)
  console.log('PASS completion and timeline detail navigation')
  await page.goto('http://tauri.localhost/settings')
  await page.getByLabel('固定背景',{exact:true}).check()
  await expect(page.getByLabel('固定背景',{exact:true})).toBeChecked()
  await page.getByLabel('随机背景',{exact:true}).check()
  await expect.poll(async()=>(await api('/settings')).mode).toBe('RANDOM')
  console.log('PASS settings persistence')
  const zip=await page.evaluate(async()=>{
    const r=await fetch('http://127.0.0.1:8080/api/backup/export',{headers:{Authorization:'Bearer '+sessionStorage.getItem('life1000.accessToken')}})
    return {status:r.status,disposition:r.headers.get('Content-Disposition'),bytes:Array.from(new Uint8Array(await r.arrayBuffer()))}
  })
  assert.equal(zip.status,200); assert.match(zip.disposition,/attachment/)
  assert.equal(Buffer.from(zip.bytes).subarray(0,2).toString(),'PK')
  await writeFile(join(root,'.cache/desktop-native/backup.zip'),Buffer.from(zip.bytes))
  console.log('PASS authenticated ZIP export through native WebView (native save dialog requires manual check)')
  await mkdir(join(root,'.cache/desktop-native'),{recursive:true})
  await page.screenshot({path:join(root,'.cache/desktop-native/settings.png')})
  await api('/settings','PUT',{HOME_BACKGROUND_MODE:originalSettings.mode,HOME_FIXED_BACKGROUND_PATH:originalSettings.fixedPath})
  originalSettings=undefined
  await api('/goals/'+slot,'DELETE'); slot=undefined
  await api('/categories/'+categoryId,'DELETE');categoryId=undefined
  await close()
  await open(); await login(); await close()
  assert.deepEqual(nativeErrors,[])
  console.log('PASS restart; no frontend runtime errors; test fixtures cleaned')
} catch(error) {
  if(page && !page.isClosed()) {
    console.error('Startup status:',await page.locator('.desktop-startup p').textContent().catch(()=>'not on startup screen'))
    await page.screenshot({path:join(root,'.cache/desktop-native/failure.png')}).catch(()=>{})
  }
  console.error(error.message)
  process.exitCode=1
} finally {
  if(page && !page.isClosed()) {
    if(originalSettings) await api('/settings','PUT',{HOME_BACKGROUND_MODE:originalSettings.mode,HOME_FIXED_BACKGROUND_PATH:originalSettings.fixedPath}).catch(()=>{})
    if(slot && createdTitle){
      const goal=await api('/goals/'+slot).catch(()=>null)
      if(goal?.title===createdTitle) await api('/goals/'+slot,'DELETE').catch(()=>{})
    }
    if(categoryId) await api('/categories/'+categoryId,'DELETE').catch(()=>{})
  }
  await close().catch(()=>{if(app?.exitCode===null) app.kill()})
}
