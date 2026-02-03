import { defineConfig, devices } from '@playwright/test';

const isCI = !!process.env.CI;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  workers: isCI ? 1 : undefined,

  timeout: 60_000,
  expect: { timeout: 10_000 },

  outputDir: 'test-results',

  reporter: isCI
    ? [['list'], ['html', { open: 'never' }]]
    : [['html', { open: 'on-failure' }]],

  use: {
    baseURL: 'http://127.0.0.1:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },

  projects: isCI
    ? [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }]
    : [
        { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
        { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
        { name: 'webkit', use: { ...devices['Desktop Safari'] } },
      ],

  webServer: {
    command: 'npm run start -- --host 127.0.0.1 --port 4200 --proxy-config proxy.conf.json',
    url: 'http://127.0.0.1:4200',
    reuseExistingServer: !isCI,
    timeout: 240000,
  },
});
