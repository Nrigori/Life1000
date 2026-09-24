import { test, expect } from '@playwright/test'

test('desktop waits for native HTTP readiness, then uses loopback API with JWT', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('life1000.accessToken', 'desktop-test-token')
    ;(window as any).__TAURI__ = { core: { invoke: (command: string) => {
      if (command !== 'start_backend') throw Error('unexpected command')
      return new Promise(resolve => { (window as any).ready = resolve })
    } } }
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
    ;(window as any).__TAURI__ = { core: { invoke: async () => {
      if (++attempts === 1) throw 'Life1000 需要 Java 21。'
    } } }
  })
  await page.goto('/login')
  await expect(page.getByRole('status')).toHaveText('Life1000 需要 Java 21。')
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.locator('.desktop-startup')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '进入', exact: true })).toBeVisible()
})
