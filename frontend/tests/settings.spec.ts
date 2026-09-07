import { test,expect,type Page } from '@playwright/test'
import type { Settings,BackgroundImage } from '../src/api/settings'
import type { Category } from '../src/api/goals'
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j6X8AAAAASUVORK5CYII=','base64')
async function mock(page:Page,empty=false) {
  const images:BackgroundImage[]=empty?[]:[1,2,3].map(id=>({attachmentId:id,slotNo:id===3?47:27,originalName:'影像 '+id+'.png',filePath:'goals/027/'+id,allowHomeBackground:id===1,fixed:false}))
  let categories:Category[]=empty?[]:[{id:1,name:'旅行',sortOrder:0},{id:2,name:'阅读',sortOrder:0}]
  const settings:Settings={mode:'RANDOM',fixedPath:null,fixedImage:null,files:{imageCount:images.length,documentCount:2,totalBytes:1471026298}}
  const calls:string[]=[]
  await page.addInitScript(()=>sessionStorage.setItem('life1000.accessToken','phase6-token'))
  await page.route('**/api/**',async route=>{
    const req=route.request(),url=new URL(req.url()),path=url.pathname,method=req.method()
    if(!path.startsWith('/api/')){await route.fallback();return}
    expect(req.headers().authorization).toBe('Bearer phase6-token');calls.push(method+' '+path)
    if(path==='/api/settings') {
      if(method==='PUT'){
        const body=req.postDataJSON()
        if(body.HOME_BACKGROUND_MODE)settings.mode=body.HOME_BACKGROUND_MODE
        if(body.HOME_FIXED_BACKGROUND_PATH){settings.fixedPath=body.HOME_FIXED_BACKGROUND_PATH;settings.fixedImage=images.find(i=>i.filePath===settings.fixedPath)||null}
      }
      await route.fulfill({json:settings});return
    }
    if(path==='/api/settings/images'){await route.fulfill({json:images});return}
    if(path==='/api/categories'){
      if(method==='POST')categories.push({id:3,...req.postDataJSON()})
      await route.fulfill({status:method==='POST'?201:200,json:method==='POST'?categories.at(-1):categories.toSorted((a,b)=>a.sortOrder-b.sortOrder||a.id-b.id)});return
    }
    if(path.startsWith('/api/categories/')){
      const id=Number(path.split('/').at(-1))
      if(method==='PUT'){categories=categories.map(c=>c.id===id?{id,...req.postDataJSON()}:c);await route.fulfill({json:categories.find(c=>c.id===id)})}
      else{categories=categories.filter(c=>c.id!==id);await route.fulfill({status:204})}return
    }
    if(path.endsWith('/home-background')){images.find(i=>path.includes('/'+i.attachmentId+'/'))!.allowHomeBackground=req.postDataJSON().allowed;await route.fulfill({json:{}});return}
    if(path.includes('/attachments/')&&path.endsWith('/content')){await route.fulfill({contentType:'image/png',body:png});return}
    if(path==='/api/home/background'){const image=settings.mode==='FIXED'?settings.fixedImage:images.find(i=>i.allowHomeBackground);await route.fulfill(image?{json:{id:image.attachmentId,originalName:image.originalName}}:{status:204});return}
    if(path==='/api/quotes/random'){await route.fulfill({status:204});return}
    if(path==='/api/stats'){await route.fulfill({json:{writtenCount:2,completedCount:1,inProgressCount:0,blankCount:998,completedThisYear:1,imageCount:3,documentCount:2,quoteCount:0}});return}
    if(path==='/api/backup/export'){await route.fulfill({contentType:'application/zip',headers:{'Content-Disposition':'attachment; filename="Life1000_Backup_2026-09-07.zip"'},body:Buffer.from('UEsFBgAAAAAAAAAAAAAAAAAAAAAAAA==','base64')});return}
    await route.fulfill({status:404,json:{message:'unexpected '+path}})
  })
  return {settings,images,calls}
}
test('settings persist modes, fixed image, candidates and home reads authenticated image',async({page})=>{
  const fixture=await mock(page)
  await page.goto('/settings')
  await expect(page.getByRole('radio',{name:'随机背景',exact:true})).toBeChecked()
  const first=page.locator('[data-image="1"]')
  await first.getByLabel('允许作为首页背景').uncheck()
  await expect.poll(()=>fixture.images[0]!.allowHomeBackground).toBe(false)
  await first.getByRole('button',{name:'设为固定首页背景'}).click()
  await expect(page.locator('.fixed-preview')).toContainText('影像 1.png')
  await page.getByRole('radio',{name:'固定背景',exact:true}).check()
  await expect.poll(()=>fixture.settings.mode).toBe('FIXED')
  await page.reload()
  await expect(page.getByRole('radio',{name:'固定背景',exact:true})).toBeChecked()
  await expect(first.getByLabel('允许作为首页背景')).not.toBeChecked()
  await page.getByRole('link',{name:'Life1000 首页',exact:true}).click()
  await expect(page.locator('.home-photo')).toHaveAttribute('src',/^blob:/)
  await page.getByRole('link',{name:'设置',exact:true}).click()
  await page.getByRole('radio',{name:'随机背景',exact:true}).check()
  await expect.poll(()=>fixture.settings.mode).toBe('RANDOM')
})
test('category CRUD stable order, confirm cancel and escaped modal',async({page})=>{
  await mock(page)
  await page.goto('/settings')
  await page.getByRole('button',{name:'＋ 新增分类'}).click()
  const dialog=page.getByRole('dialog')
  await dialog.getByLabel('名称',{exact:true}).fill('摄影')
  await dialog.getByLabel('排序',{exact:true}).fill('-1')
  await dialog.getByRole('button',{name:'保存',exact:true}).click()
  const item=page.locator('[data-category="3"]')
  await expect(page.locator('.category-list li').first()).toContainText('摄影')
  await item.getByRole('button',{name:'编辑',exact:true}).click()
  await dialog.getByLabel('名称',{exact:true}).fill('影像')
  await dialog.getByRole('button',{name:'保存',exact:true}).click()
  await page.reload()
  await expect(item).toContainText('影像')
  await item.getByRole('button',{name:'删除',exact:true}).click()
  await expect(dialog).toContainText('不会删除人生事项')
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
  await expect(item).toBeVisible()
  await item.getByRole('button',{name:'删除',exact:true}).click()
  await dialog.getByRole('button',{name:'确认删除'}).click()
  await expect(item).toHaveCount(0)
})
test('backup download, file totals, four sections and laptop sizing',async({page})=>{
  const fixture=await mock(page)
  await page.goto('/settings')
  await expect(page.locator('.file-usage')).toContainText('1.37 GB')
  const download=page.waitForEvent('download')
  await page.getByRole('button',{name:'导出完整备份',exact:true}).click()
  expect((await download).suggestedFilename()).toBe('Life1000_Backup_2026-09-07.zip')
  expect(fixture.calls.filter(c=>c==='GET /api/backup/export')).toHaveLength(1)
  for(const width of [1440,1366,390]){
    await page.setViewportSize({width,height:width===1440?900:768})
    await page.goto('/settings')
    await expect(page.locator('.settings-section')).toHaveCount(4)
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
    if(width>1000)await page.screenshot({path:'../.cache/phase6-settings-'+width+'.png',fullPage:true})
    await page.getByRole('button',{name:'＋ 新增分类'}).click()
    const bounds=await page.getByRole('dialog').boundingBox()
    expect(bounds!.y).toBeGreaterThanOrEqual(0);expect(bounds!.y+bounds!.height).toBeLessThanOrEqual(768+132)
    await page.keyboard.press('Escape')
  }
})
test('empty settings, loading and failed mutations preserve visible state and input',async({page})=>{
  await mock(page,false)
  await page.route('**/api/settings',async route=>{
    if(route.request().method()==='PUT')await route.fulfill({status:503,json:{message:'保存失败，请重试。'}})
    else await route.fallback()
  })
  await page.goto('/settings')
  await page.getByRole('radio',{name:'固定背景',exact:true}).check()
  await expect(page.getByRole('alert')).toContainText('保存失败')
  await expect(page.getByRole('radio',{name:'随机背景',exact:true})).toBeChecked()
  await page.getByRole('button',{name:'＋ 新增分类'}).click()
  await page.route('**/api/categories',route=>route.request().method()==='POST'?route.fulfill({status:503,json:{message:'保存失败'}}):route.fallback())
  await page.getByRole('dialog').getByLabel('名称',{exact:true}).fill('留下输入')
  await page.getByRole('dialog').getByRole('button',{name:'保存',exact:true}).click()
  await expect(page.getByRole('dialog').getByLabel('名称',{exact:true})).toHaveValue('留下输入')
  await page.keyboard.press('Escape')
  await page.unrouteAll()
  await mock(page,true)
  await page.goto('/settings')
  await expect(page.getByText('还没有图片。可以先在人生事项中留下影像。')).toBeVisible()
  await expect(page.getByText('还没有分类。事项也可以保持“不分类”。')).toBeVisible()
  await expect(page.getByText('固定背景未选择或已经失效；固定模式下将使用纸张默认背景。')).toBeVisible()
})


test('loading stays visible and export prevents duplicate requests',async({page})=>{
  const fixture=await mock(page,true)
  let releaseLoad!:()=>void
  const loading=new Promise<void>(resolve=>{releaseLoad=resolve})
  await page.route('**/api/settings',async route=>{await loading;await route.fallback()})
  await page.goto('/settings')
  await expect(page.getByRole('status')).toContainText('正在翻阅设置')
  releaseLoad()
  await expect(page.locator('.settings-section')).toHaveCount(4)
  let releaseExport!:()=>void
  const pending=new Promise<void>(resolve=>{releaseExport=resolve})
  await page.route('**/api/backup/export',async route=>{await pending;await route.fallback()})
  await page.getByRole('button',{name:'导出完整备份',exact:true}).click()
  await expect(page.getByRole('button',{name:'正在整理备份…'})).toBeDisabled()
  await expect(page.getByRole('status')).toContainText('请保持页面打开')
  const download=page.waitForEvent('download')
  releaseExport();await download
  await expect(page.getByRole('button',{name:'导出完整备份',exact:true})).toBeEnabled()
  expect(fixture.calls.filter(c=>c==='GET /api/backup/export')).toHaveLength(1)
})
