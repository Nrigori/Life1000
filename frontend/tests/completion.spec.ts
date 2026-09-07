import { expect, test, type Page } from '@playwright/test'
import type { LifeGoal } from '../src/api/goals'
import type { Completion } from '../src/api/completion'
import type { Attachment } from '../src/api/details'
const year = new Date().getFullYear()
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j6X8AAAAASUVORK5CYII=', 'base64')
async function mock(page: Page) {
  const goals = new Map<number, LifeGoal>([12,27,31,47].map(slot => [slot, { id: slot, slotNo: slot,
    title: slot === 27 ? '独自去一次西藏旅行' : '留下第' + slot + '件回忆', status: slot === 27 ? 'IN_PROGRESS' : 'COMPLETED',
    categoryId: 1, reason: '想沿着自己的步调，看看世界。', coverAttachmentId: null }]))
  const archives = new Map<number, Completion>([12,31,47].map(slot => [slot, { id: slot, goalId: slot, completedDate: `${slot === 47 ? year - 1 : year}-01-17`,
    completionNote: null, rating: null, statusBeforeCompletion: 'NOT_STARTED' }]))
  let attachments: (Attachment & { goalId: number })[] = []
  const mutations: string[] = []
  let next = 100
  const currentGoal = (slot: number) => ({ ...goals.get(slot)!, completedDate: goals.get(slot)?.status === 'COMPLETED' ? archives.get(slot)?.completedDate : null })
  const timeline = () => [...goals.values()].filter(goal => goal.status === 'COMPLETED' && archives.has(goal.slotNo)).map(goal => ({
    year: Number(archives.get(goal.slotNo)!.completedDate.slice(0,4)), completedDate: archives.get(goal.slotNo)!.completedDate, slotNo: goal.slotNo, title: goal.title,
  })).sort((a,b) => a.completedDate.localeCompare(b.completedDate) || a.slotNo-b.slotNo)
  await page.addInitScript(() => sessionStorage.setItem('life1000.accessToken','phase4-token'))
  await page.route('**/api/**', async route => {
    const req = route.request(), path = new URL(req.url()).pathname, method = req.method()
    if (!path.startsWith('/api/')) { await route.fallback(); return }
    expect(req.headers().authorization).toBe('Bearer phase4-token')
    if (method !== 'GET') mutations.push(method + ' ' + path)
    if (path === '/api/categories') { await route.fulfill({ json: [{ id: 1, name: '旅行' }] }); return }
    if (path === '/api/goals/range' || path === '/api/goals/search') {
      const status = new URL(req.url()).searchParams.get('status')
      await route.fulfill({ json: [...goals.keys()].map(currentGoal).filter(goal => !status || goal.status === status) }); return
    }
    if (path === '/api/timeline') {
      const counts = new Map<number,number>([[year,0]])
      for (const value of timeline()) counts.set(value.year,(counts.get(value.year)||0)+1)
      await route.fulfill({ json: [...counts].sort((a,b)=>b[0]-a[0]).map(([year,count])=>({ year,count })) }); return
    }
    if (path.startsWith('/api/timeline/')) { await route.fulfill({ json: timeline().filter(value=>value.year === Number(path.split('/').at(-1))) }); return }
    const goalRoute = /^\/api\/goals\/(\d+)(.*)$/.exec(path)
    if (goalRoute) {
      const slot = Number(goalRoute[1]), suffix = goalRoute[2], goal = goals.get(slot)
      if (!goal) { await route.fulfill({ status: 404, json: { message: '该编号尚未写下' } }); return }
      if (!suffix) {
        if (method === 'DELETE') { goals.delete(slot); archives.delete(slot); attachments=attachments.filter(file=>file.goalId!==slot); await route.fulfill({ status: 204 }) }
        else await route.fulfill({ json: currentGoal(slot) })
        return
      }
      if (suffix === '/check-items' || suffix === '/records') { await route.fulfill({ json: [] }); return }
      if (suffix === '/attachments') { await route.fulfill({ json: attachments.filter(file=>file.goalId===slot) }); return }
      if (suffix === '/cover') {
        const value = attachments.find(file=>file.id === goal.coverAttachmentId) || [...attachments].reverse().find(file=>file.goalId===slot && file.isImage)
        await route.fulfill(value ? { json: value } : { status: 204 }); return
      }
      if (suffix === '/completion' && method === 'GET') { await route.fulfill(archives.has(slot) ? { json: archives.get(slot) } : { status: 204 }); return }
      if (suffix === '/uncomplete') { goal.status=archives.get(slot)!.statusBeforeCompletion; await route.fulfill({ status: 204 }); return }
      if (suffix === '/complete' || suffix === '/completion') {
        let input: { completedDate: string; completionNote: string|null; rating: number|null }
        if (req.headers()['content-type']?.startsWith('multipart/')) {
          const body = req.postDataBuffer()!.toString()
          input = JSON.parse(/\r\n\r\n(\{"completedDate":[^\r\n]+)\r\n/.exec(body)![1]!)
          for (const match of body.matchAll(/name="files"; filename="([^"]+)"/g)) {
            attachments.push({ id: ++next, goalId: slot, recordId: null, stage: 'COMPLETION', originalName: match[1]!,
              isImage: match[1]!.endsWith('.png'), mimeType: match[1]!.endsWith('.png') ? 'image/png' : 'text/plain', fileSize: 50, allowHomeBackground: false })
          }
        } else input = req.postDataJSON()
        const existing = archives.get(slot)
        const archive: Completion = { id: existing?.id || ++next, goalId: slot, ...input,
          statusBeforeCompletion: goal.status === 'COMPLETED' ? existing!.statusBeforeCompletion : goal.status }
        archives.set(slot, archive); goal.status='COMPLETED'
        await route.fulfill({ json: archive }); return
      }
    }
    const fileRoute = /^\/api\/attachments\/(\d+)(.*)$/.exec(path)
    if (fileRoute) {
      const id=Number(fileRoute[1]), file=attachments.find(file=>file.id===id)!
      if (method === 'DELETE') { attachments=attachments.filter(file=>file.id!==id); await route.fulfill({ status: 204 }); return }
      await route.fulfill({ contentType: file.mimeType, body: file.isImage ? png : Buffer.from('proof') }); return
    }
    throw new Error('Unexpected ' + method + ' ' + path)
  })
  return { goals, archives, mutations, files: () => attachments }
}
test('card/list quick completion requires a dialog; cancel and minimal completion preserve the slot', async ({ page }) => {
  const api=await mock(page)
  await page.goto('/goals')
  const tile=page.locator('[data-slot="27"]')
  await tile.getByRole('button',{name:'标记为完成'}).click()
  await expect(page.getByLabel('完成日期',{exact:true})).not.toHaveValue('')
  await page.getByLabel('完成证明',{exact:true}).setInputFiles({name:'unused.txt',mimeType:'text/plain',buffer:Buffer.from('unused')})
  expect(api.mutations).toHaveLength(0)
  await page.getByRole('button',{name:'取消',exact:true}).click()
  expect(api.goals.get(27)?.status).toBe('IN_PROGRESS')
  await page.getByRole('button',{name:'列表',exact:true}).click()
  await tile.getByRole('button',{name:'标记为完成'}).click()
  await page.getByLabel('完成日期',{exact:true}).fill('')
  await page.getByRole('button',{name:'确认完成',exact:true}).click()
  expect(api.mutations).toHaveLength(0)
  await page.getByLabel('完成日期',{exact:true}).fill(`${year}-06-17`)
  await page.getByRole('button',{name:'确认完成',exact:true}).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(tile).toContainText(`${year}.06.17`)
  await expect(tile).toContainText('027')
  await expect(tile.getByRole('button',{name:'已完成',exact:true})).toBeDisabled()
  await expect(page.getByRole('status')).toContainText('第027件，已经成为回忆。')
  await expect(page.locator('.completion-feedback')).toHaveCount(0,{timeout:6000})
  expect(api.archives.get(27)?.rating).toBeNull()
  expect(api.archives.get(27)?.completionNote).toBeNull()
  expect(api.files()).toHaveLength(0)
  await page.getByRole('button',{name:'卡片',exact:true}).click()
  await expect(tile).toHaveClass(/memory-tile/)
})
test('detail proof, undo, recompletion, editing date, timeline link and completed deletion', async ({ page }) => {
  const api=await mock(page)
  await page.goto('/goals/27')
  await page.getByRole('button',{name:'○ 标记为完成',exact:true}).click()
  await page.getByLabel('完成日期',{exact:true}).fill(`${year}-06-17`)
  await page.getByLabel('完成感想',{exact:true}).fill('真正做完以后，想记住这一天。')
  await page.getByRole('button',{name:'5 星',exact:true}).click()
  await page.getByLabel('完成证明',{exact:true}).setInputFiles([
    {name:'proof.png',mimeType:'image/png',buffer:png},
    {name:'proof.txt',mimeType:'text/plain',buffer:Buffer.from('proof')},
  ])
  await page.screenshot({path:'../.cache/phase4-completion-dialog.png'})
  await page.getByRole('button',{name:'确认完成',exact:true}).click()
  await expect(page.locator('.completion-section')).toContainText('真正做完以后')
  await expect(page.locator('.completion-section')).toContainText('★★★★★')
  await expect(page.locator('.completion-section img')).toBeVisible()
  const id=api.archives.get(27)!.id
  expect(api.files()).toHaveLength(2)
  await page.reload()
  await expect(page.locator('.completion-section')).toContainText(`${year}.06.17`)
  await page.getByRole('button',{name:'更多',exact:true}).click()
  await page.getByRole('button',{name:'撤销完成',exact:true}).click()
  await page.getByRole('button',{name:'取消',exact:true}).click()
  expect(api.goals.get(27)?.status).toBe('COMPLETED')
  await page.getByRole('button',{name:'撤销完成',exact:true}).click()
  await page.getByRole('button',{name:'确认撤销',exact:true}).click()
  await expect(page.locator('.completion-section')).toHaveCount(0)
  expect(api.goals.get(27)?.status).toBe('IN_PROGRESS')
  expect(api.files()).toHaveLength(2)
  await page.getByRole('link',{name:'时间轴',exact:true}).click()
  await expect(page.locator('.year-content')).not.toContainText('独自去一次西藏旅行')
  await page.goto('/goals/27')
  await page.getByRole('button',{name:'○ 标记为完成',exact:true}).click()
  await expect(page.getByLabel('完成感想',{exact:true})).toHaveValue('真正做完以后，想记住这一天。')
  await expect(page.getByRole('button',{name:'5 星',exact:true})).toHaveAttribute('aria-pressed','true')
  await expect(page.locator('.retained-proofs')).toContainText('proof.txt')
  await page.getByLabel('完成日期',{exact:true}).fill(`${year}-06-17`)
  await page.getByRole('button',{name:'确认完成',exact:true}).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(api.archives.get(27)!.id).toBe(id)
  expect(api.files()).toHaveLength(2)
  await page.getByRole('button',{name:'编辑完成档案',exact:true}).click()
  await page.getByLabel('完成日期',{exact:true}).fill(`${year+1}-01-02`)
  await page.getByRole('button',{name:'不评分',exact:true}).click()
  await page.getByLabel('完成感想',{exact:true}).fill('')
  await page.getByRole('button',{name:'保存完成档案',exact:true}).click()
  await expect(page.locator('.archive-rating')).toHaveCount(0)
  await expect(page.locator('.completion-section .prose')).toHaveCount(0)
  await page.getByRole('link',{name:'时间轴',exact:true}).click()
  await expect(page.locator('.timeline-year').first()).toHaveAttribute('data-year',String(year+1))
  await expect(page.locator('[data-year="'+year+'"] .year-toggle')).toHaveAttribute('aria-expanded','true')
  const future=page.locator('[data-year="'+(year+1)+'"]')
  await expect(future.locator('.year-toggle')).toHaveAttribute('aria-expanded','false')
  await future.locator('.year-toggle').click()
  await expect(future).toContainText('01.02')
  await expect(future).toContainText('第027件')
  await page.screenshot({path:'../.cache/phase4-timeline.png',fullPage:true})
  await future.getByRole('link').click()
  await expect(page).toHaveURL(/\/goals\/27$/)
  await page.locator('.attachment-card').filter({has:page.getByRole('heading',{name:'proof.png',exact:true})}).getByRole('button',{name:'删除附件'}).click()
  await page.getByRole('button',{name:'确认删除',exact:true}).click()
  await expect(page.locator('.attachment-card')).toHaveCount(1)
  await page.getByRole('button',{name:'更多',exact:true}).click()
  await page.getByRole('button',{name:'清空这个编号',exact:true}).click()
  await page.getByRole('button',{name:'确认清空',exact:true}).click()
  await expect(page.locator('[data-slot="27"]')).toContainText('尚未写下')
  await page.getByRole('link',{name:'时间轴',exact:true}).click()
  await expect(page.locator('[data-year="'+(year+1)+'"]')).toHaveCount(0)
})
test('timeline defaults, counts and stable ties; failed completion keeps inputs and state', async ({ page }) => {
  const api=await mock(page)
  await page.goto('/timeline')
  const current=page.locator('[data-year="'+year+'"]')
  await expect(current).toContainText('完成 2 件')
  await expect(current.locator('a')).toHaveText(['01.17第012件 · 留下第12件回忆','01.17第031件 · 留下第31件回忆'])
  await expect(page.locator('.timeline-page img')).toHaveCount(0)
  await page.setViewportSize({width:390,height:844})
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
  await page.goto('/goals/27')
  await page.getByRole('button',{name:'○ 标记为完成',exact:true}).click()
  await page.getByLabel('完成感想',{exact:true}).fill('不能丢失的文字')
  await page.route('**/api/goals/27/complete',route=>route.fulfill({status:503,json:{message:'数据库暂时不可用'}}))
  await page.getByRole('button',{name:'确认完成',exact:true}).click()
  await expect(page.getByRole('alert')).toHaveText('数据库暂时不可用')
  await expect(page.getByLabel('完成感想',{exact:true})).toHaveValue('不能丢失的文字')
  expect(api.goals.get(27)?.status).toBe('IN_PROGRESS')
  await page.getByRole('button',{name:'取消',exact:true}).click()
  await page.route('**/api/timeline', route=>route.fulfill({json:[{year,count:0}]}))
  await page.route('**/api/timeline/'+year,route=>route.fulfill({json:[]}))
  await page.goto('/timeline')
  await expect(page.getByText('这一年没有完成记录。')).toBeVisible()
})
