import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import type { LifeGoal } from '../src/api/goals'

function goal(slotNo: number, title: string, categoryId: number | null, status: LifeGoal['status']): LifeGoal {
  return { id: slotNo + 100, slotNo, title, categoryId, status, reason: null, coverAttachmentId: null }
}

async function mockApi(page: Page, authenticated = true) {
  const goals = new Map<number, LifeGoal>([
    [3, goal(3, '独自去西藏旅行', 1, 'IN_PROGRESS')],
    [27, goal(27, '学会一首钢琴曲', 2, 'NOT_STARTED')],
    [118, goal(118, '去海边看一次日出', 1, 'COMPLETED')],
    [406, goal(406, '坐一次长途火车旅行', 1, 'NOT_STARTED')],
  ])
  const creates: unknown[] = []
  if (authenticated) {
    await page.addInitScript(() => sessionStorage.setItem('life1000.accessToken', 'browser-test-token'))
  }
  await page.route('**/api/**', async route => {
    const req = route.request()
    const url = new URL(req.url())
    const path = url.pathname
    if (!path.startsWith('/api/')) { await route.fallback(); return }
    if (path === '/api/auth/login') {
      expect(req.headers().authorization).toBeUndefined()
      const input = req.postDataJSON()
      if (input.username !== 'tester' || input.password !== 'test-password') {
        await route.fulfill({ status: 401, json: { message: '账号或密码错误' } })
      } else {
        await route.fulfill({ json: { accessToken: 'browser-test-token', tokenType: 'Bearer', expiresIn: 7200 } })
      }
      return
    }
    expect(req.headers().authorization).toBe('Bearer browser-test-token')
    if (/^\/api\/goals\/\d+\/(cover|completion)$/.test(path)) {
      await route.fulfill({ status: 204 })
    } else if (/^\/api\/goals\/\d+\/(check-items|records|attachments)$/.test(path)) {
      await route.fulfill({ json: [] })
    } else if (path === '/api/categories') {
      await route.fulfill({ json: [{ id: 1, name: '旅行', sortOrder: 0 }, { id: 2, name: '学习', sortOrder: 1 }] })
    } else if (path === '/api/health') {
      await route.fulfill({ json: { status: 'UP' } })
    } else if (path === '/api/goals/range') {
      expect(url.searchParams.get('fromSlot')).toBe('1')
      expect(url.searchParams.get('toSlot')).toBe('1000')
      await route.fulfill({ json: [...goals.values()] })
    } else if (path === '/api/goals/search') {
      const keyword = url.searchParams.get('keyword') || ''
      const categoryId = url.searchParams.get('categoryId')
      const status = url.searchParams.get('status')
      await route.fulfill({ json: [...goals.values()].filter(item =>
        item.title.includes(keyword) && (!categoryId || item.categoryId === Number(categoryId))
        && (!status || item.status === status)).sort((a, b) => a.slotNo - b.slotNo) })
    } else if (/^\/api\/goals\/\d+$/.test(path)) {
      const slot = Number(path.split('/').at(-1))
      if (req.method() === 'POST') {
        if (goals.has(slot)) {
          await route.fulfill({ status: 409, json: { message: '该编号已经写下，请编辑已有事项' } })
          return
        }
        const body = req.postDataJSON()
        creates.push({ slot, ...body })
        expect(Object.keys(body).sort()).toEqual(['categoryId', 'reason', 'title'])
        const created = { ...goal(slot, body.title, body.categoryId, 'NOT_STARTED'), reason: body.reason }
        goals.set(slot, created)
        await route.fulfill({ status: 201, json: created })
      } else if (req.method() === 'DELETE') {
        goals.delete(slot)
        await route.fulfill({ status: 204 })
      } else {
        await route.fulfill(goals.has(slot) ? { json: goals.get(slot) } : { status: 404, json: { message: '该编号尚未写下' } })
      }
    } else {
      throw new Error('Unexpected API: ' + req.method() + ' ' + path)
    }
  })
  return { goals, creates }
}

const tile = (page: Page, slot: number) => page.locator(`[data-slot="${slot}"]`)
const numbers = (page: Page) => page.locator('.slot-number').allTextContents()

test('login uses existing API, then goals opens with five columns and blank slots', async ({ page }) => {
  await mockApi(page, false)
  await page.goto('/goals')
  await expect(page).toHaveURL(/\/login(?:\?.*)?$/)
  await page.getByLabel('账号', { exact: true }).fill('tester')
  await page.getByLabel('密码', { exact: true }).fill('wrong')
  await page.getByRole('button', { name: '进入', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('账号或密码错误')
  await page.getByLabel('密码', { exact: true }).fill('test-password')
  await page.getByRole('button', { name: '进入', exact: true }).click()
  await expect(page).toHaveURL('http://127.0.0.1:5180/goals')
  await page.getByRole('link', { name: '人生千事', exact: true }).click()
  await expect(page.getByRole('heading', { name: '人生千事', exact: true })).toBeVisible()
  await expect(page.locator('.goal-card')).toHaveCount(50)
  await expect(tile(page, 1)).toContainText('001')
  await expect(tile(page, 1)).toContainText('尚未写下')
  await expect(tile(page, 3)).toContainText('独自去西藏旅行')
  await expect(tile(page, 3)).toContainText('尚无影像')
  await expect(tile(page, 3).getByRole('button', { name: '标记为完成' })).toBeEnabled()
  const box = await tile(page, 1).boundingBox()
  expect(box?.width).toBeGreaterThanOrEqual(220)
  expect(box?.width).toBeLessThanOrEqual(240)
  expect(box?.height).toBe(270)
  expect(await page.locator('.goals-grid').evaluate(el => getComputedStyle(el).gridTemplateColumns.split(' ').length)).toBe(5)
  await page.screenshot({ path: '../.cache/phase2-goals-1440.png' })
})

test('continuous scrolling reveals exactly 001–1000 without renumbering', async ({ page }) => {
  await mockApi(page)
  await page.goto('/goals')
  await expect(page.locator('.goal-card')).toHaveCount(50)
  for (let count = 50; count < 1000; count += 50) {
    await page.getByRole('button', { name: '继续向下展开' }).scrollIntoViewIfNeeded()
    await expect.poll(() => page.locator('.goal-card').count()).toBeGreaterThanOrEqual(count + 50)
  }
  await expect(page.locator('.goal-card')).toHaveCount(1000)
  expect(await numbers(page)).toEqual(Array.from({ length: 1000 }, (_, i) => String(i + 1).padStart(3, '0')))
  await expect(tile(page, 1000)).toContainText('尚未写下')
})

test('create in 037 updates same card, list switches, confirmed deletion restores 037', async ({ page }) => {
  const api = await mockApi(page)
  await page.goto('/goals')
  await tile(page, 37).getByRole('button').click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByLabel('标题 必填', { exact: true }).fill('去山间看星空')
  await page.getByLabel('分类 可选').selectOption('1')
  await page.getByLabel('为什么想做 可选').fill('想记住那片夜空')
  await page.getByRole('button', { name: '写下它', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(tile(page, 37)).toContainText('去山间看星空')
  await expect(tile(page, 37)).toContainText('未开始')
  expect(api.creates).toEqual([{ slot: 37, title: '去山间看星空', categoryId: 1, reason: '想记住那片夜空' }])
  await page.getByRole('button', { name: '列表', exact: true }).click()
  await expect(page.locator('.goal-row')).toHaveCount(50)
  await expect(tile(page, 37)).toContainText('037')
  await expect(tile(page, 37)).toContainText('旅行')
  await tile(page, 37).getByRole('link').click()
  await expect(page).toHaveURL(/\/goals\/37$/)
  await page.getByRole('button', { name: '更多', exact: true }).click()
  await page.getByRole('button', { name: '清空这个编号' }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  expect(api.goals.has(37)).toBe(true)
  await page.getByRole('button', { name: '清空这个编号' }).click()
  await page.getByRole('button', { name: '确认清空' }).click()
  await expect(page).toHaveURL(/\/goals$/)
  await expect(tile(page, 37)).toContainText('尚未写下')
  await expect(tile(page, 38)).toContainText('038')
  await expect(tile(page, 3)).toContainText('独自去西藏旅行')
  expect(api.goals.has(37)).toBe(false)
})

test('search, category and all three status filters keep original numbers in both views', async ({ page }) => {
  await mockApi(page)
  await page.goto('/goals')
  await expect(page.locator('.goal-card')).toHaveCount(50)
  await page.getByRole('searchbox').fill('旅行')
  await expect.poll(() => numbers(page)).toEqual(['003', '406'])
  await page.getByRole('button', { name: '列表', exact: true }).click()
  await expect(page.locator('.goal-row')).toHaveCount(2)
  await page.getByRole('button', { name: '清除筛选' }).click()
  await expect(page.locator('.goal-row')).toHaveCount(50)
  await page.getByLabel('分类筛选').selectOption('1')
  await expect.poll(() => numbers(page)).toEqual(['003', '118', '406'])
  for (const [value, expected] of [
    ['IN_PROGRESS', ['003']], ['NOT_STARTED', ['406']], ['COMPLETED', ['118']],
  ] as const) {
    await page.getByLabel('状态筛选').selectOption(value)
    await expect.poll(() => numbers(page)).toEqual([...expected])
  }
  await page.getByRole('button', { name: '清除筛选' }).click()
  await expect(page.locator('.goal-row')).toHaveCount(50)
  await page.getByRole('searchbox').fill('没有这种事项')
  await expect(page.getByText('没有找到符合条件的事项。')).toBeVisible()
  await expect(page.locator('[data-slot]')).toHaveCount(0)
  await page.getByRole('button', { name: '清除筛选' }).click()
  await page.getByRole('button', { name: '卡片', exact: true }).click()
  await expect(tile(page, 1)).toContainText('尚未写下')
})

test('network errors do not turn unknown records into blank slots and retry works', async ({ page }) => {
  await mockApi(page)
  await page.route('**/api/goals/range?*', route => route.fulfill({ status: 503, json: { message: '数据库暂时不可用' } }))
  await page.goto('/goals')
  await expect(page.getByRole('alert')).toHaveText('数据库暂时不可用')
  await expect(page.locator('[data-slot]')).toHaveCount(0)
  await page.unroute('**/api/goals/range?*')
  await page.getByRole('button', { name: '重新读取' }).click()
  await expect(page.locator('[data-slot]')).toHaveCount(50)
})

test('cancel and failed creation keep blank slot; conflict refreshes without overwriting', async ({ page }) => {
  const api = await mockApi(page)
  await page.goto('/goals')
  await tile(page, 7).getByRole('button').click()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(api.creates).toHaveLength(0)
  await tile(page, 7).getByRole('button').click()
  await page.getByLabel('标题 必填', { exact: true }).fill('  ')
  await page.getByRole('button', { name: '写下它', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('请先写下标题。')
  expect(api.creates).toHaveLength(0)
  await page.getByLabel('标题 必填', { exact: true }).fill('我的新事项')
  await page.route('**/api/goals/7', route => route.fulfill({ status: 503, json: { message: '稍后再试' } }))
  await page.getByRole('button', { name: '写下它', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('稍后再试')
  await expect(page.getByLabel('标题 必填', { exact: true })).toHaveValue('我的新事项')
  await expect(tile(page, 7)).toContainText('尚未写下')
  await page.unroute('**/api/goals/7')
  api.goals.set(7, goal(7, '另一窗口已写下', null, 'NOT_STARTED'))
  await page.getByRole('button', { name: '写下它', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('该编号已经写下，请编辑已有事项')
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(tile(page, 7)).toContainText('另一窗口已写下')
  expect(api.creates).toHaveLength(0)
})

test('expired login returns to login page and permits a new login', async ({ page }) => {
  await mockApi(page)
  await page.route('**/api/categories', route => route.fulfill({ status: 401, json: { message: '请重新登录' } }))
  await page.goto('/goals')
  await expect(page).toHaveURL(/\/login(?:\?.*)?$/)
  expect(await page.evaluate(() => sessionStorage.getItem('life1000.accessToken'))).toBeNull()
  await page.unroute('**/api/categories')
  await page.getByLabel('账号', { exact: true }).fill('tester')
  await page.getByLabel('密码', { exact: true }).fill('test-password')
  await page.getByRole('button', { name: '进入', exact: true }).click()
  await expect(page).toHaveURL('http://127.0.0.1:5180/goals')
})

test('late search responses never replace the newest selection', async ({ page }) => {
  await mockApi(page)
  await page.goto('/goals')
  await expect(page.locator('[data-slot]')).toHaveCount(50)
  await page.route('**/api/goals/search?*', async route => {
    if (new URL(route.request().url()).searchParams.get('keyword') === '旧查询') {
      await new Promise(resolve => setTimeout(resolve, 800))
      await route.fulfill({ json: [goal(99, '旧查询内容', null, 'NOT_STARTED')] })
    } else {
      await route.fallback()
    }
  })
  const pending = page.waitForRequest(req => req.url().includes(encodeURIComponent('旧查询')))
  await page.getByRole('searchbox').fill('旧查询')
  await pending
  await page.getByRole('searchbox').fill('旅行')
  await expect.poll(() => numbers(page)).toEqual(['003', '406'])
  await page.waitForTimeout(1000)
  expect(await numbers(page)).toEqual(['003', '406'])
})

test('1366px keeps five columns and narrow screens have no horizontal overflow', async ({ page }) => {
  await mockApi(page)
  await page.setViewportSize({ width: 1366, height: 768 })
  await page.goto('/goals')
  await expect(page.locator('.goal-card')).toHaveCount(50)
  expect(await page.locator('.goals-grid').evaluate(el => getComputedStyle(el).gridTemplateColumns.split(' ').length)).toBe(5)
  await page.screenshot({ path: '../.cache/phase2-goals-1366.png' })
  await page.setViewportSize({ width: 600, height: 800 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})
