import { expect, test, type Page } from '@playwright/test'
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j6X8AAAAASUVORK5CYII=', 'base64')
const markdown = '# 我的记录\n\n一段 **粗体** 和 *斜体*。\n\n> 引用\n\n- 第一项\n- 第二项\n\n\x60\x60\x60js\nconst hello = 1\n\x60\x60\x60\n\n---\n\n<script>window.previewAttacked=true</script>\n<img src="https://preview.invalid/track" onerror="window.previewAttacked=true"><a href="javascript:window.previewAttacked=true">恶意链接</a><svg onload="window.previewAttacked=true"></svg>'
async function setup(page: Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('life1000.accessToken', 'preview-token')
    const create = URL.createObjectURL.bind(URL)
    const revoke = URL.revokeObjectURL.bind(URL)
    ;(window as any).created = []
    ;(window as any).revoked = []
    URL.createObjectURL = blob => { const url = create(blob); (window as any).created.push(url); return url }
    URL.revokeObjectURL = url => { (window as any).revoked.push(url); revoke(url) }
  })
  const files = [
    { id:1, name:'notes.md', image:false, stage:'PROCESS', recordId:1 },
    { id:2, name:'notes.txt', image:false, stage:'GENERAL', recordId:null },
    { id:3, name:'notes.pdf', image:false, stage:'COMPLETION', recordId:null },
    { id:4, name:'photo.png', image:true, stage:'GENERAL', recordId:null },
    { id:5, name:'archive.zip', image:false, stage:'GENERAL', recordId:null },
    { id:6, name:'office.docx', image:false, stage:'GENERAL', recordId:null },
    { id:7, name:'second.png', image:true, stage:'PROCESS', recordId:1 },
    { id:8, name:'third.png', image:true, stage:'COMPLETION', recordId:null },
    { id:9, name:'fourth.png', image:true, stage:'GENERAL', recordId:null },
  ].map(file => ({ id:file.id, originalName:file.name, mimeType:file.image?'image/png':'application/octet-stream',
    isImage:file.image, fileSize:30, recordId:file.recordId, stage:file.stage, allowHomeBackground:false }))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (!path.startsWith('/api/')) { await route.fallback(); return }
    expect(route.request().headers().authorization).toBe('Bearer preview-token')
    if (/\/attachments\/\d+\/content$/.test(path)) {
      const id=Number(path.split('/')[3])
      const file=files.find(value=>value.id===id)!
      await route.fulfill({body:file.isImage?png:id===1?markdown:id===3?'%PDF-1.4\n%%EOF':'第一行\n第二行 <script>只作为文本</script>',contentType:file.isImage?'image/png':id===3?'application/pdf':'text/plain'})
    } else if (path==='/api/goals/27') await route.fulfill({json:{id:27,slotNo:27,title:'附件预览测试',status:'COMPLETED',reason:'',categoryId:null}})
    else if (path.endsWith('/attachments')) await route.fulfill({json:files})
    else if (path.endsWith('/cover')) await route.fulfill({status:204})
    else if (path.endsWith('/completion')) await route.fulfill({json:{completedDate:'2026-09-08',completionNote:null,rating:null}})
    else if (path.endsWith('/records')) await route.fulfill({json:[{id:1,recordDate:'2026-09-08',content:'记录',goalId:27}]})
    else await route.fulfill({json:[]})
  })
  await page.goto('/goals/27')
  await expect(page.locator('.attachment-card')).toHaveCount(9)
}
test('Markdown renders common syntax and strips executable HTML and external resources', async ({page}) => {
  await setup(page)
  let external = false
  page.on('request', req => { if(req.url().includes('preview.invalid')) external=true })
  await page.locator('[data-attachment="1"]').getByRole('button',{name:'预览',exact:true}).click()
  const dialog=page.getByRole('dialog')
  await expect(dialog.getByRole('heading',{name:'我的记录'})).toBeVisible()
  await expect(dialog.locator('strong')).toHaveText('粗体')
  await expect(dialog.locator('em')).toHaveText('斜体')
  await expect(dialog.locator('blockquote')).toContainText('引用')
  await expect(dialog.locator('li')).toHaveCount(2)
  await expect(dialog.locator('pre code')).toContainText('const hello = 1')
  await expect(dialog.locator('hr')).toHaveCount(1)
  await expect(dialog.locator('script,img,svg,iframe,[href],[onerror]')).toHaveCount(0)
  expect(await page.evaluate(() => (window as any).previewAttacked)).toBeUndefined()
  expect(external).toBe(false)
})
test('TXT preserves newlines, downloads still use authenticated download=true',async({page})=>{
  await setup(page)
  await page.locator('[data-attachment="2"]').getByRole('button',{name:'预览',exact:true}).click()
  await expect(page.getByRole('dialog').locator('pre')).toHaveText('第一行\n第二行 <script>只作为文本</script>')
  const request=page.waitForRequest(req=>req.url().includes('/attachments/2/content?download=true'))
  const download=page.waitForEvent('download')
  await page.getByRole('dialog').getByRole('button',{name:'下载',exact:true}).click()
  expect((await download).suggestedFilename()).toBe('notes.txt')
  expect((await request).headers().authorization).toBe('Bearer preview-token')
})
for (const [id,selector] of [[3,'iframe'],[4,'img']] as const) {
  test(`preview ${selector} uses a Blob URL and releases it on Escape`,async({page})=>{
    await setup(page)
    await page.locator(`[data-attachment="${id}"]`).getByRole('button',{name:'预览',exact:true}).click()
    const element=page.getByRole('dialog').locator(selector)
    await expect(element).toHaveAttribute('src',/^blob:/)
    const url=await element.getAttribute('src')
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(await page.evaluate(url=>(window as any).revoked.includes(url),url)).toBe(true)
  })
}
test('unsupported formats only download, and record/proof locations offer previews',async({page})=>{
  await setup(page)
  for(const id of [5,6]) {
    await expect(page.locator(`[data-attachment="${id}"]`).getByRole('button',{name:'预览',exact:true})).toHaveCount(0)
    await expect(page.locator(`[data-attachment="${id}"]`).getByRole('button',{name:'下载',exact:true})).toBeVisible()
  }
  await page.locator('.record-list .record-files').getByRole('button',{name:'notes.md · 预览'}).click()
  await expect(page.getByRole('dialog')).toContainText('我的记录')
  await page.keyboard.press('Escape')
  await page.locator('.completion-section .record-files').getByRole('button',{name:'notes.pdf · 预览'}).click()
  await expect(page.getByRole('dialog').locator('iframe')).toHaveAttribute('src',/^blob:/)
})
test('read failure is understandable and closing a pending preview cannot resurrect it',async({page})=>{
  await setup(page)
  await page.route('**/api/attachments/2/content?download=false',r=>r.fulfill({status:404,json:{message:'附件文件不存在'}}))
  await page.locator('[data-attachment="2"]').getByRole('button',{name:'预览',exact:true}).click()
  await expect(page.getByRole('dialog').getByRole('alert')).toHaveText('附件文件不存在')
  await page.keyboard.press('Escape')
  await page.route('**/api/attachments/3/content?download=false',async r=>{
    await new Promise(resolve=>setTimeout(resolve,300))
    await r.fulfill({body:'%PDF-1.4',contentType:'application/pdf'}).catch(()=>{})
  })
  await page.locator('[data-attachment="3"]').getByRole('button',{name:'预览',exact:true}).click()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
})

test('retained proof preview closes independently without dismissing completion editing',async({page})=>{
  await setup(page)
  await page.getByRole('button',{name:'编辑完成档案'}).click()
  await page.locator('.retained-proofs > div').filter({hasText:'notes.pdf'}).getByRole('button',{name:'预览',exact:true}).click()
  await expect(page.locator('.attachment-preview-dialog iframe')).toHaveAttribute('src',/^blob:/)
  await page.keyboard.press('Escape')
  await expect(page.locator('.attachment-preview-dialog')).toHaveCount(0)
  await expect(page.locator('.completion-dialog')).toBeVisible()
  await expect(page.getByLabel('完成日期',{exact:true})).toHaveValue('2026-09-08')
})
test('preview fits a 1366px laptop and WebP uses the browser image decoder',async({page})=>{
  await page.setViewportSize({width:1366,height:768})
  await setup(page)
  const webp=await page.evaluate(()=>{
    const canvas=document.createElement('canvas');canvas.width=2;canvas.height=2
    return canvas.toDataURL('image/webp').split(',')[1]!
  })
  await page.route('**/api/attachments/4/content?download=false',r=>r.fulfill({body:Buffer.from(webp,'base64'),contentType:'image/webp'}))
  await page.locator('[data-attachment="4"]').getByRole('button',{name:'预览',exact:true}).click()
  await expect(page.getByRole('dialog').locator('img')).toBeVisible()
  expect(await page.getByRole('dialog').locator('img').evaluate(img=>(img as HTMLImageElement).naturalWidth)).toBe(2)
  const bounds=await page.getByRole('dialog').boundingBox()
  expect(bounds!.x).toBeGreaterThanOrEqual(0)
  expect(bounds!.y).toBeGreaterThanOrEqual(0)
  expect(bounds!.y+bounds!.height).toBeLessThanOrEqual(768)
})

test('image viewer navigates only current-goal images, zooms, pans, downloads and releases its cache',async({page})=>{
  await setup(page)
  await expect(page.locator('.attachment-card img')).toHaveCount(4)
  const createdBefore=await page.evaluate(()=>(window as any).created.length as number)
  await page.locator('[data-attachment="4"]').getByRole('button',{name:'预览',exact:true}).click()
  const viewer=page.locator('.image-viewer')
  await expect(viewer).toBeVisible()
  await expect(viewer).toContainText('1 / 4')
  await expect(viewer).toContainText('photo.png')

  await page.getByRole('button',{name:'下一张图片'}).click()
  await expect(viewer).toContainText('2 / 4')
  await expect(viewer).toContainText('second.png')
  await page.keyboard.press('ArrowRight')
  await expect(viewer).toContainText('3 / 4')
  await page.keyboard.press('ArrowLeft')
  await expect(viewer).toContainText('2 / 4')

  const image=viewer.locator('img')
  const stage=viewer.locator('.image-viewer-stage')
  await stage.dispatchEvent('wheel',{deltaY:-120})
  await expect.poll(()=>image.evaluate(element=>element.style.transform)).toContain('scale(1.18)')
  const box=await stage.boundingBox()
  await page.mouse.move(box!.x+box!.width/2,box!.y+box!.height/2)
  await page.mouse.down()
  await page.mouse.move(box!.x+box!.width/2+45,box!.y+box!.height/2+25)
  await page.mouse.up()
  await expect.poll(()=>image.evaluate(element=>element.style.transform)).toContain('translate3d(45px, 25px, 0px)')
  await page.getByRole('button',{name:'原始大小'}).click()
  await expect(image).toHaveClass(/original/)
  await page.getByRole('button',{name:'适应窗口'}).click()
  await expect(image).toHaveClass(/fit/)

  const content=page.waitForRequest(request=>request.url().includes('/attachments/7/content?download=true'))
  const download=page.waitForEvent('download')
  await viewer.getByRole('button',{name:'下载',exact:true}).click()
  expect((await download).suggestedFilename()).toBe('second.png')
  expect((await content).headers().authorization).toBe('Bearer preview-token')

  await page.keyboard.press('Escape')
  await expect(viewer).toHaveCount(0)
  await expect.poll(async()=>page.evaluate(start=>{
    const revoked=(window as any).revoked as string[]
    return ((window as any).created as string[]).slice(start).every(url=>revoked.includes(url))
  },createdBefore)).toBe(true)
})
