import { test, expect, type Page } from '@playwright/test'
import { calendar } from '../src/home/calendar'
import type { Quote } from '../src/api/home'
const stats = { writtenCount:126, completedCount:37, inProgressCount:12, blankCount:874, completedThisYear:9, imageCount:186, documentCount:23, quoteCount:2 }
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j6X8AAAAASUVORK5CYII=', 'base64')
async function mock(page:Page, photo=false, empty=false, authenticated=true) {
  const quotes: Quote[] = empty ? [] : [{id:1,content:'此心安处是吾乡。',source:'苏轼《定风波》',includeHome:true},{id:2,content:'沿着自己的步调。',source:null,includeHome:false}]
  const calls:string[] = []
  if(authenticated) await page.addInitScript(()=>sessionStorage.setItem('life1000.accessToken','phase5-token'))
  await page.route('**/api/**', async route=>{
    const req=route.request(), url=new URL(req.url()), path=url.pathname, method=req.method()
    if (!path.startsWith('/api/')) { await route.fallback(); return }
    calls.push(method+' '+path)
    if(path==='/api/auth/login') { await route.fulfill({json:{accessToken:'phase5-token'}}); return }
    expect(req.headers().authorization).toBe('Bearer phase5-token')
    if(path==='/api/settings') {await route.fulfill({json:{mode:'RANDOM',fixedPath:null,fixedImage:null,files:{imageCount:0,documentCount:0,totalBytes:0}}});return}
    if(path==='/api/settings/images'){await route.fulfill({json:[]});return}
    if(path==='/api/stats') {await route.fulfill({json:stats});return}
    if(path==='/api/home/background') {await route.fulfill(photo?{json:{id:77,originalName:'测试图片.png'}}:{status:204});return}
    if(path==='/api/attachments/77/content') {expect(url.searchParams.has('token')).toBeFalsy();await route.fulfill({body:png,contentType:'image/png'});return}
    if(path==='/api/quotes/random') {const q=quotes.find(q=>q.includeHome);await route.fulfill(q?{json:q}:{status:204});return}
    if(path==='/api/quotes' && method==='GET') {const k=url.searchParams.get('keyword')||'';await route.fulfill({json:quotes.filter(q=>q.content.includes(k)||(q.source||'').includes(k))});return}
    if(path==='/api/quotes' && method==='POST') {const q={id:3,...req.postDataJSON()};quotes.unshift(q);await route.fulfill({status:201,json:q});return}
    if(path.startsWith('/api/quotes/')) {const id=Number(path.split('/').at(-1)),index=quotes.findIndex(q=>q.id===id);if(method==='PUT'){quotes[index]={id,...req.postDataJSON()};await route.fulfill({json:quotes[index]})}else{quotes.splice(index,1);await route.fulfill({status:204})}return}
    if(path==='/api/categories'||path.startsWith('/api/goals/')||path.startsWith('/api/timeline')){await route.fulfill({json:[]});return}
    await route.fulfill({status:404,json:{message:'Unexpected mock route '+path}})
  })
  return {quotes,calls}
}
test('local calendar uses leap-year day count and stable day ordinals',()=>{
  expect(calendar(new Date(2028,0,1))).toEqual({year:2028,days:366,percent:0,date:'2028.01.01'})
  expect(calendar(new Date(2028,2,1)).percent).toBeCloseTo(60/366*100)
  expect(calendar(new Date(2027,2,1)).percent).toBeCloseTo(59/365*100)
  expect(calendar(new Date(2028,11,31)).percent).toBeCloseTo(365/366*100)
  expect(calendar(new Date(2026,8,7)).date).toBe('2026.09.07')
})
test('empty home remains a one-screen cover at laptop sizes; five navigation destinations',async({page})=>{
  await mock(page,false,true)
  for(const width of [1440,1366]) {
    await page.setViewportSize({width,height:width===1440?900:768})
    await page.goto('/')
    await expect(page.getByRole('heading',{name:'LIFE / 1000'})).toBeVisible()
    await expect(page.locator('.home-counts')).toContainText('37 / 126')
    await expect(page.locator('.home-photo')).toHaveCount(0)
    await expect(page.locator('.home-quote')).toHaveCount(0)
    expect(await page.evaluate(()=>document.documentElement.scrollHeight<=innerHeight+1)).toBeTruthy()
    await page.screenshot({path:'../.cache/phase5-home-'+width+'.png'})
  }
  for(const [name,path] of [['人生千事','/goals'],['时间轴','/timeline'],['数据统计','/stats'],['金句收藏','/quotes'],['设置','/settings']]) {
    await page.goto('/')
    await page.getByRole('link',{name,exact:true}).click()
    await expect(page).toHaveURL(new RegExp(path+'$'))
  }
})
test('photo uses authenticated Blob, randomizes only on entry and releases URLs',async({page})=>{
  const fixture=await mock(page,true)
  await page.addInitScript(()=>{
    const original=URL.revokeObjectURL.bind(URL)
    ;(window as any).revoked=[]
    URL.revokeObjectURL=(url:string)=>{(window as any).revoked.push(url);original(url)}
  })
  await page.goto('/')
  await expect(page.locator('.home-photo')).toHaveAttribute('src',/^blob:/)
  await expect(page.locator('.home-quote')).toContainText('苏轼《定风波》')
  const first=await page.locator('.home-photo').getAttribute('src')
  await page.screenshot({path:'../.cache/phase5-home-photo.png'})
  expect(fixture.calls.filter(c=>c==='GET /api/home/background')).toHaveLength(1)
  await page.getByRole('link',{name:'金句收藏',exact:true}).click()
  await expect.poll(()=>page.evaluate(url=>(window as any).revoked.includes(url),first)).toBeTruthy()
  await page.getByRole('link',{name:'Life1000 首页',exact:true}).click()
  await expect(page.locator('.home-photo')).toHaveAttribute('src',/^blob:/)
  expect(await page.locator('.home-photo').getAttribute('src')).not.toBe(first)
  expect(fixture.calls.filter(c=>c==='GET /api/home/background')).toHaveLength(2)
})
test('quotes create edit search toggle persist and confirm deletion',async({page})=>{
  const fixture=await mock(page)
  await page.goto('/quotes')
  await page.getByRole('button',{name:'＋ 收藏',exact:true}).click()
  const dialog=page.getByRole('dialog')
  await expect(dialog.getByLabel('参与首页随机')).toBeChecked()
  await dialog.getByLabel('内容',{exact:true}).fill('慢慢走，认真生活。')
  await dialog.getByLabel('来源',{exact:true}).fill('自己的记录')
  await dialog.getByRole('button',{name:'保存',exact:true}).click()
  const entry=page.locator('[data-quote="3"]')
  await expect(entry).toContainText('慢慢走，认真生活。')
  await entry.getByLabel('参与首页随机').uncheck()
  await expect.poll(()=>fixture.quotes.find(q=>q.id===3)?.includeHome).toBe(false)
  await page.reload()
  await expect(entry.getByLabel('参与首页随机')).not.toBeChecked()
  await entry.getByRole('button',{name:'编辑',exact:true}).click()
  await dialog.getByLabel('内容',{exact:true}).fill('留一点时间给自己。')
  await dialog.getByLabel('来源',{exact:true}).fill('')
  await dialog.getByRole('button',{name:'保存',exact:true}).click()
  await expect(entry).toContainText('留一点时间给自己。')
  await page.getByRole('searchbox').fill('定风波')
  await expect(page.locator('.quote-entry')).toHaveCount(1)
  await expect(page.locator('.quote-entry')).toContainText('此心安处')
  await page.getByRole('searchbox').fill('留一点')
  await expect(entry).toBeVisible()
  await expect(page.locator('.quote-entry')).toHaveCount(1)
  await page.getByRole('searchbox').fill('')
  await expect(page.locator('.quote-entry')).toHaveCount(3)
  await page.screenshot({path:'../.cache/phase5-quotes.png'})
  await entry.getByRole('button',{name:'删除',exact:true}).click()
  await dialog.getByRole('button',{name:'取消',exact:true}).click()
  await expect(entry).toBeVisible()
  await entry.getByRole('button',{name:'删除',exact:true}).click()
  await dialog.getByRole('button',{name:'确认删除',exact:true}).click()
  await expect(entry).toHaveCount(0)
  await page.reload()
  await expect(entry).toHaveCount(0)
})
test('stats displays all quiet numbers without charts',async({page})=>{
  await mock(page)
  await page.goto('/stats')
  const rows=page.locator('.quiet-stats > div')
  for(const [index,value] of ['126','37','12','874','9 件','186 张图片 · 23 个文档','2 条'].entries()) await expect(rows.nth(index).locator('dd')).toHaveText(value)
  await expect(page.locator('canvas,svg')).toHaveCount(0)
  await page.screenshot({path:'../.cache/phase5-stats.png'})
})
test('failed background falls back to paper while quote and stats remain usable',async({page})=>{
  await mock(page,true)
  await page.route('**/api/attachments/77/content*',route=>route.fulfill({status:404,json:{message:'已删除'}}))
  await page.goto('/')
  await expect(page.getByRole('alert')).toContainText('先以纸张为封面')
  await expect(page.locator('.home-photo')).toHaveCount(0)
  await expect(page.locator('.home-counts')).toContainText('37 / 126')
  await expect(page.locator('.home-quote')).toContainText('此心安处')
})


test('login returns to intended route and defaults safely to home',async({page})=>{
  await mock(page,false,false,false)
  for(const [start,destination] of [['/quotes','/quotes'],['/login','/'],['/login?redirect=https%3A%2F%2Fexample.com','/']]){
    await page.goto('/login')
    await page.evaluate(()=>sessionStorage.clear())
    await page.goto(start)
    await expect(page).toHaveURL(/\/login(?:\?.*)?$/)
    await page.getByLabel('账号',{exact:true}).fill('test')
    await page.getByLabel('密码',{exact:true}).fill('test')
    await page.getByRole('button',{name:'进入',exact:true}).click()
    await expect(page).toHaveURL('http://127.0.0.1:5180'+destination)
    await expect(page.locator(destination==='/'?'.home-counts':'.quote-list')).toBeVisible()
  }
})
