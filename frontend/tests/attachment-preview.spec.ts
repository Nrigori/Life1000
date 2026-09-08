import { expect, test, type Page } from '@playwright/test'
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j6X8AAAAASUVORK5CYII=', 'base64')
const markdown = '# 我的记录\n\n一段 **粗体** 和 *斜体*。\n\n> 引用\n\n- 第一项\n- 第二项\n\n\x60\x60\x60js\nconst hello = 1\n\x60\x60\x60\n\n---\n\n<script>window.previewAttacked=true</script>\n<img src="https://preview.invalid/track" onerror="window.previewAttacked=true"><a href="javascript:window.previewAttacked=true">恶意链接</a><svg onload="window.previewAttacked=true"></svg>'
async function setup(page: Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('life1000.accessToken', 'preview-token')
    const revoke = URL.revokeObjectURL.bind(URL)
    ;(window as any).revoked = []
    URL.revokeObjectURL = url => { (window as any).revoked.push(url); revoke(url) }
  })
  const files = ['notes.md','notes.txt','notes.pdf','photo.png','archive.zip','office.docx'].map((name,index) => ({
    id:index+1, originalName:name, mimeType:index===3?'image/png':'application/octet-stream',
    isImage:index===3, fileSize:30, recordId:index===0?1:null, stage:index===0?'PROCESS':index===2?'COMPLETION':'GENERAL', allowHomeBackground:false,
  }))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (!path.startsWith('/api/')) { await route.fallback(); return }
    expect(route.request().headers().authorization).toBe('Bearer preview-token')
    if (/\/attachments\/\d+\/content$/.test(path)) {
      const id=Number(path.split('/')[3])
      await route.fulfill({body:id===4?png:id===1?markdown:id===3?'%PDF-1.4\n%%EOF':'第一行\n第二行 <script>只作为文本</script>',contentType:id===4?'image/png':id===3?'application/pdf':'text/plain'})
    } else if (path==='/api/goals/27') await route.fulfill({json:{id:27,slotNo:27,title:'附件预览测试',status:'COMPLETED',reason:'',categoryId:null}})
    else if (path.endsWith('/attachments')) await route.fulfill({json:files})
    else if (path.endsWith('/cover')) await route.fulfill({status:204})
    else if (path.endsWith('/completion')) await route.fulfill({json:{completedDate:'2026-09-08',completionNote:null,rating:null}})
    else if (path.endsWith('/records')) await route.fulfill({json:[{id:1,recordDate:'2026-09-08',content:'记录',goalId:27}]})
    else await route.fulfill({json:[]})
  })
  await page.goto('/goals/27')
  await expect(page.locator('.attachment-card')).toHaveCount(6)
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
  await page.locator('.record-list .record-files').getByRole('button').click()
  await expect(page.getByRole('dialog')).toContainText('我的记录')
  await page.keyboard.press('Escape')
  await page.locator('.completion-section .record-files').getByRole('button').click()
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
  await page.locator('.retained-proofs').getByRole('button',{name:'预览',exact:true}).click()
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
