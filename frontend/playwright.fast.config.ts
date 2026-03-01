import { defineConfig, devices } from '@playwright/test';

const isCI = !!process.env.CI;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  workers: isCI ? 1 : undefined,
  maxFailures: isCI ? 20 : 5,
  globalTimeout: 30 * 60_000,

  timeout: 60_000,
  expect: { timeout: 10_000 },

  outputDir: 'test-results',

  reporter: isCI
    ? [['list'], ['html', { open: 'never' }]]
    : [['html', { open: 'on-failure' }]],

  use: {
    baseURL: 'http://127.0.0.1:4200',
    headless: true,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    launchOptions: {
      args: ['--disable-dev-shm-usage', '--no-sandbox', '--disable-gpu'],
    },
  },

  projects: isCI
    ? [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }]
    : [
        { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
        { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
        { name: 'webkit', use: { ...devices['Desktop Safari'] } },
      ],

  webServer: {
    command: `npm run start -- --host 127.0.0.1 --port 4200 --proxy-config ${isCI ? 'proxy.conf.e2e.json' : 'proxy.conf.json'}`,
    command: 'node --max-old-space-size=4096 ./node_modules/@angular/cli/bin/ng serve --host 127.0.0.1 --port 4200 --proxy-config proxy.e2e.conf.json',
    url: 'http://127.0.0.1:4200',
    reuseExistingServer: true,
    timeout: 240000,
  },
});
