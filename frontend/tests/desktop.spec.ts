import { test, expect } from '@playwright/test'

test('desktop waits for native HTTP readiness, then uses loopback API with JWT', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('life1000.accessToken', 'desktop-test-token')
    ;(window as any).__TAURI__ = {
      core: { invoke: (command: string) => {
        if (command !== 'start_backend') throw Error('unexpected command')
        return new Promise(resolve => { (window as any).ready = resolve })
      } },
      window: { getCurrentWindow: () => ({
        minimize: async () => {}, toggleMaximize: async () => {}, close: async () => {},
        startDragging: async () => {}, isMaximized: async () => false,
      }) },
    }
  })
  let calls = 0
  await page.route('http://127.0.0.1:8080/api/**', async route => {
    calls++
    expect(route.request().headers().authorization).toBe('Bearer desktop-test-token')
    await route.fulfill({ json: { writtenCount: 0, completedCount: 0, inProgressCount: 0, blankCount: 1000,
      completedThisYear: 0, imageCount: 0, documentCount: 0, quoteCount: 0 } })
  })
  await page.goto('/stats')
  await expect(page.getByRole('status')).toHaveText('正在打开你的记录册…')
  expect(calls).toBe(0)
  await page.evaluate(() => (window as any).ready())
  await expect(page.locator('.desktop-startup')).toHaveCount(0)
  await expect.poll(() => calls).toBeGreaterThan(0)
})

test('desktop startup errors remain readable and can be retried', async ({ page }) => {
  await page.addInitScript(() => {
    let attempts = 0
    ;(window as any).__TAURI__ = {
      core: { invoke: async () => {
        if (++attempts === 1) throw 'Life1000 需要 Java 21。'
      } },
      window: { getCurrentWindow: () => ({
        minimize: async () => {}, toggleMaximize: async () => {}, close: async () => {},
        startDragging: async () => {}, isMaximized: async () => false,
      }) },
    }
  })
  await page.goto('/login')
  await expect(page.getByRole('status')).toHaveText('Life1000 需要 Java 21。')
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.locator('.desktop-startup')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '进入', exact: true })).toBeVisible()
})

test('desktop titlebar is available during startup and keeps interactive areas out of drag handling', async ({ page }) => {
  await page.addInitScript(() => {
    const calls: string[] = []
    let maximized = false
    ;(window as any).nativeWindowCalls = calls
    ;(window as any).__TAURI__ = {
      core: { invoke: () => new Promise(() => {}) },
      window: { getCurrentWindow: () => ({
        minimize: async () => { calls.push('minimize') },
        toggleMaximize: async () => { maximized = !maximized; calls.push('toggleMaximize') },
        close: async () => { calls.push('close') },
        startDragging: async () => { calls.push('startDragging') },
        isMaximized: async () => maximized,
      }) },
    }
  })
  await page.goto('/goals')
  await expect(page.locator('.desktop-titlebar')).toBeVisible()
  await expect(page.getByRole('status')).toHaveText('正在打开你的记录册…')

  await page.getByRole('link', { name: '设置', exact: true }).click()
  await expect(page).toHaveURL(/\/settings$/)
  expect(await page.evaluate(() => (window as any).nativeWindowCalls)).toEqual([])

  const drag = page.getByTestId('window-drag-region')
  await drag.dispatchEvent('mousedown', { button: 0, buttons: 1, detail: 1 })
  await expect.poll(() => page.evaluate(() => (window as any).nativeWindowCalls)).toContain('startDragging')
  await drag.dispatchEvent('mousedown', { button: 0, buttons: 1, detail: 2 })
  await expect.poll(() => page.evaluate(() => (window as any).nativeWindowCalls)).toContain('toggleMaximize')
  await expect(page.getByRole('button', { name: '还原', exact: true })).toBeVisible()

  await page.getByRole('button', { name: '最小化', exact: true }).click()
  await page.getByRole('button', { name: '关闭', exact: true }).click()
  await expect.poll(() => page.evaluate(() => (window as any).nativeWindowCalls)).toEqual([
    'startDragging', 'toggleMaximize', 'minimize', 'close',
  ])
})
