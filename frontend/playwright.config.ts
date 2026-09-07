import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  timeout: 60000,
  fullyParallel: true,
  workers: 2,
  reporter: 'list',
  outputDir: '../.cache/playwright-results',
  use: {
    baseURL: 'http://127.0.0.1:5180',
    channel: process.env.PLAYWRIGHT_CHANNEL || 'msedge',
    viewport: { width: 1440, height: 900 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'npm run dev -- --port 5180',
    url: 'http://127.0.0.1:5180',
    reuseExistingServer: false,
    timeout: 60000,
  },
})
