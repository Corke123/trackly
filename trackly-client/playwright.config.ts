import { defineConfig, devices } from '@playwright/test';

const PORT = 4300;

/**
 * The journeys run against the built SPA with the gateway's API stubbed per test (see
 * `e2e/fixtures/board-api.ts`). That keeps them fast and hermetic enough to gate every commit;
 * the services' own behaviour is already covered by their Testcontainers integration tests.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  // Each test stubs the API in its own page, so nothing is shared and nothing needs serialising.
  workers: process.env['CI'] ? 2 : undefined,
  reporter: process.env['CI'] ? [['github'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: `npx ng serve --port ${PORT} --configuration development`,
    url: `http://localhost:${PORT}`,
    reuseExistingServer: !process.env['CI'],
    timeout: 120_000,
  },
});
