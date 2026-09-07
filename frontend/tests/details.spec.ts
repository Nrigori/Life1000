import { expect, test, type Page } from '@playwright/test'
import type { LifeGoal } from '../src/api/goals'
import type { CheckItem, GoalRecord, Attachment } from '../src/api/details'

const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j6X8AAAAASUVORK5CYII=', 'base64')
async function mockDetail(page: Page, completed = false) {
  let goal: LifeGoal = { id: 27, slotNo: 27, title: '独自去远方旅行', categoryId: 1,
    status: completed ? 'COMPLETED' : 'IN_PROGRESS', reason: '想沿着自己的步调，\n看看从未见过的风景。', coverAttachmentId: null }
  let checks: CheckItem[] = []
  let records: GoalRecord[] = []
  let files: Attachment[] = []
  let id = 0
  await page.addInitScript(() => sessionStorage.setItem('life1000.accessToken', 'detail-token'))
  await page.route('**/api/**', async route => {
    const req = route.request(), path = new URL(req.url()).pathname, method = req.method()
    if (!path.startsWith('/api/')) { await route.fallback(); return }
    expect(req.headers().authorization).toBe('Bearer detail-token')
    if (path === '/api/goals/27/completion') { await route.fulfill({ status: 204 }); return }
    if (path === '/api/categories') { await route.fulfill({ json: [{ id: 1, name: '旅行' }, { id: 2, name: '学习' }] }); return }
    if (path === '/api/goals/27') {
      if (method === 'PUT') {
        const body = req.postDataJSON()
        expect(body.status).not.toBe('COMPLETED')
        goal = { ...goal, ...body, status: body.status || goal.status }
      }
      await route.fulfill({ json: goal }); return
    }
    if (path === '/api/goals/27/cover') {
      const image = files.find(f => f.id === goal.coverAttachmentId) || [...files].reverse().find(f => f.isImage)
      await route.fulfill(image ? { json: image } : { status: 204 }); return
    }
    if (/^\/api\/goals\/27\/cover\/\d+$/.test(path)) {
      goal.coverAttachmentId = Number(path.split('/').at(-1))
      await route.fulfill({ status: 204 }); return
    }
    if (path === '/api/goals/27/check-items') {
      if (method === 'POST') { const item = { ...req.postDataJSON(), id: ++id, sortOrder: id }; checks.push(item); await route.fulfill({ status: 201, json: item }) }
      else await route.fulfill({ json: checks })
      return
    }
    if (path.startsWith('/api/check-items/')) {
      const value = Number(path.split('/').at(-1))
      if (method === 'DELETE') { checks = checks.filter(c => c.id !== value); await route.fulfill({ status: 204 }) }
      else { checks = checks.map(c => c.id === value ? { ...c, ...req.postDataJSON() } : c); await route.fulfill({ json: checks.find(c => c.id === value) }) }
      return
    }
    if (path === '/api/goals/27/records') {
      if (method === 'POST') { const record = { ...req.postDataJSON(), id: ++id }; records.push(record); await route.fulfill({ status: 201, json: record }) }
      else await route.fulfill({ json: records })
      return
    }
    if (path.startsWith('/api/records/')) {
      const value = Number(path.split('/').at(-1))
      if (method === 'DELETE') {
        records = records.filter(r => r.id !== value); files = files.filter(f => f.recordId !== value)
        if (!files.some(f => f.id === goal.coverAttachmentId)) goal.coverAttachmentId = null
        await route.fulfill({ status: 204 })
      } else { records = records.map(r => r.id === value ? { ...r, ...req.postDataJSON() } : r); await route.fulfill({ json: records.find(r => r.id === value) }) }
      return
    }
    if (path === '/api/goals/27/attachments') {
      if (method === 'POST') {
        expect(req.headers()['content-type']).toContain('multipart/form-data; boundary=')
        const body = req.postDataBuffer()!.toString()
        const name = /filename="([^"]+)"/.exec(body)![1]!
        const process = body.includes('\r\nPROCESS\r\n')
        const record = /name="recordId"\r\n\r\n(\d+)/.exec(body)
        const file: Attachment = { id: ++id, originalName: name, isImage: name.endsWith('.png'),
          fileSize: 50, mimeType: name.endsWith('.png') ? 'image/png' : 'application/octet-stream',
          recordId: record ? Number(record[1]) : null, stage: process ? 'PROCESS' : 'GENERAL', allowHomeBackground: false }
        files.push(file); await route.fulfill({ status: 201, json: file })
      } else await route.fulfill({ json: files })
      return
    }
    if (/^\/api\/attachments\/\d+\/content$/.test(path)) {
      const file = files.find(f => f.id === Number(path.split('/')[3]))!
      await route.fulfill({ contentType: file.isImage ? 'image/png' : 'application/octet-stream', body: file.isImage ? png : Buffer.from('route document') }); return
    }
    if (/^\/api\/attachments\/\d+\/home-background$/.test(path)) {
      const file = files.find(f => f.id === Number(path.split('/')[3]))!
      file.allowHomeBackground = req.postDataJSON().allowed
      await route.fulfill({ json: file }); return
    }
    if (/^\/api\/attachments\/\d+$/.test(path) && method === 'DELETE') {
      const value = Number(path.split('/').at(-1)); files = files.filter(f => f.id !== value)
      if (goal.coverAttachmentId === value) goal.coverAttachmentId = null
      await route.fulfill({ status: 204 }); return
    }
    throw new Error('Unexpected API ' + method + ' ' + path)
  })
  return { goal: () => goal, files: () => files, records: () => records }
}
async function newRecord(page: Page) {
  await page.getByRole('button', { name: '写一条记录' }).click()
  await page.getByLabel('记录日期', { exact: true }).fill('2026-09-03')
  await page.getByLabel('记录正文').fill('开始研究路线。')
  await page.getByRole('button', { name: '保存记录' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.locator('.record-list')).toContainText('开始研究路线。')
}

test('detail reads like a notebook, persists edits, optional check CRUD and record CRUD', async ({ page }) => {
  const api = await mockDetail(page)
  await page.goto('/goals/027')
  await expect(page.getByRole('heading', { name: '独自去远方旅行' })).toBeVisible()
  await expect(page.locator('.eyebrow')).toContainText('027')
  await expect(page.locator('.detail-banner')).toContainText('尚无影像')
  await expect(page.locator('textarea, input:not([type=file])')).toHaveCount(0)
  await page.screenshot({ path: '../.cache/phase3-detail-reading.png', fullPage: true })
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await page.getByLabel('标题', { exact: true }).fill('独自去山间旅行')
  await page.getByLabel('分类', { exact: true }).selectOption('2')
  await page.getByLabel('状态', { exact: true }).selectOption('NOT_STARTED')
  await expect(page.getByLabel('状态').locator('option')).toHaveCount(2)
  await page.getByLabel('为什么想做', { exact: true }).fill('留下一段自己的记忆。\n慢慢走。')
  await page.getByRole('button', { name: '保存修改' }).click()
  await expect(page.locator('.basic-edit')).toHaveCount(0)
  await page.reload()
  await expect(page.locator('.detail-meta')).toHaveText('学习 · 未开始')
  await expect(page.locator('.reason-section')).toContainText('慢慢走。')
  for (const content of ['独自出发', '旅行七天']) {
    await page.getByRole('button', { name: '新增条件' }).click()
    await page.getByLabel('条件内容').fill(content)
    await page.getByRole('button', { name: '保存条件' }).click()
    await expect(page.getByRole('dialog')).toHaveCount(0)
  }
  await page.getByRole('checkbox', { name: '独自出发' }).check()
  await expect(page.getByRole('checkbox', { name: '独自出发' })).toBeChecked()
  await page.getByRole('checkbox', { name: '独自出发' }).uncheck()
  await page.getByRole('button', { name: '修改条件：独自出发' }).click()
  await page.getByLabel('条件内容').fill('独自坐火车出发')
  await page.getByRole('button', { name: '保存条件' }).click()
  await expect(page.locator('.check-list li').first()).toContainText('独自坐火车出发')
  await page.getByRole('button', { name: '删除条件：独自坐火车出发' }).click()
  await page.getByRole('button', { name: '确认删除' }).click()
  await expect(page.locator('.check-list li')).toHaveCount(1)
  await expect(page.locator('.check-list li')).toContainText('旅行七天')
  await newRecord(page)
  await page.getByRole('button', { name: '编辑记录' }).click()
  await page.getByLabel('记录正文').fill('路线终于有了眉目。')
  await page.getByRole('button', { name: '保存记录' }).click()
  await page.reload()
  await expect(page.locator('.record-list')).toContainText('路线终于有了眉目。')
  await page.getByRole('button', { name: '删除记录' }).click()
  await page.getByRole('button', { name: '确认删除' }).click()
  await expect(page.locator('.record-list > li')).toHaveCount(0)
  expect(api.records()).toHaveLength(0)
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('general and process files survive reload, preview/download, cover fallback and background flag', async ({ page }) => {
  const api = await mockDetail(page)
  await page.goto('/goals/27')
  await newRecord(page)
  await page.getByLabel('上传普通附件').setInputFiles([
    { name: 'first.png', mimeType: 'image/png', buffer: png },
    { name: 'route.txt', mimeType: 'text/plain', buffer: Buffer.from('route document') },
  ])
  await expect(page.locator('.attachment-card')).toHaveCount(2)
  await page.getByLabel('添加记录附件 2026-09-03').setInputFiles({ name: 'recent.png', mimeType: 'image/png', buffer: png })
  await expect(page.locator('.attachment-card')).toHaveCount(3)
  expect(api.files().find(f => f.originalName === 'recent.png')?.stage).toBe('PROCESS')
  expect(api.files().find(f => f.originalName === 'recent.png')?.recordId).not.toBeNull()
  await page.reload()
  await expect(page.locator('.attachment-card')).toHaveCount(3)
  await expect(page.locator('.record-files img')).toBeVisible()
  await expect(page.locator('.detail-banner img')).toBeVisible()
  const first = page.locator('.attachment-card').filter({ has: page.getByRole('heading', { name: 'first.png', exact: true }) })
  await first.getByRole('button', { name: '设为封面' }).click()
  await expect(first.getByRole('button', { name: '已选为封面' })).toBeDisabled()
  expect(api.goal().coverAttachmentId).toBe(api.files().find(f => f.originalName === 'first.png')?.id)
  await first.getByRole('checkbox', { name: '允许作为首页背景' }).check()
  await page.reload()
  await expect(first.getByRole('checkbox')).toBeChecked()
  await first.getByRole('checkbox').uncheck()
  await page.getByRole('button', { name: '预览 first.png' }).click()
  await expect(page.getByRole('dialog').getByRole('img')).toBeVisible()
  await page.keyboard.press('Escape')
  const download = page.waitForEvent('download')
  await page.locator('.attachment-card').filter({ has: page.getByRole('heading', { name: 'route.txt' }) }).getByRole('button', { name: '下载' }).click()
  expect((await download).suggestedFilename()).toBe('route.txt')
  await page.screenshot({ path: '../.cache/phase3-detail-files.png', fullPage: true })
  await first.getByRole('button', { name: '删除附件' }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(first).toBeVisible()
  await first.getByRole('button', { name: '删除附件' }).click()
  await page.getByRole('button', { name: '确认删除' }).click()
  await expect(first).toHaveCount(0)
  expect(api.goal().coverAttachmentId).toBeNull()
  await expect(page.locator('.detail-banner img')).toBeVisible()
  await page.getByRole('button', { name: '删除记录' }).click()
  await page.getByRole('button', { name: '确认删除' }).click()
  await expect(page.locator('.attachment-card')).toHaveCount(1)
  await expect(page.locator('.detail-banner')).toContainText('尚无影像')
})

test('completed goals can edit prose without completion/undo controls; failed save retains input', async ({ page }) => {
  await mockDetail(page, true)
  await page.goto('/goals/27')
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await expect(page.locator('.basic-edit')).toContainText('已完成')
  await expect(page.getByLabel('状态', { exact: true })).toHaveCount(0)
  await page.getByLabel('标题', { exact: true }).fill('保留这段回忆')
  await page.route('**/api/goals/27', async route => {
    if (route.request().method() === 'PUT') await route.fulfill({ status: 503, json: { message: '稍后再试' } })
    else await route.fallback()
  })
  await page.getByRole('button', { name: '保存修改' }).click()
  await expect(page.getByRole('alert')).toHaveText('稍后再试')
  await expect(page.getByLabel('标题', { exact: true })).toHaveValue('保留这段回忆')
  await page.unroute('**/api/goals/27')
  await page.getByRole('button', { name: '保存修改' }).click()
  await expect(page.locator('.basic-edit')).toHaveCount(0)
  await expect(page.locator('.detail-meta')).toContainText('已完成')
})
