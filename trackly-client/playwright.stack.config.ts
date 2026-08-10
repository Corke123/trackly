import { defineConfig, devices } from '@playwright/test';

/**
 * The full-stack journeys, run by hand against `docker compose up`: a real sign-in at
 * identity-service with PKCE, a real board behind the gateway's token relay, and a real 403 when a
 * plain user reaches for an admin endpoint.
 *
 * Deliberately not part of `npm run e2e` — it needs the whole stack, which the commit-stage
 * pipeline does not have. It belongs against a deployed staging environment once continuous
 * delivery is wired up (ADR 0010).
 *
 *   docker compose up --build -d
 *   npm run e2e:stack
 *
 * It writes to whichever board it finds, so run it against a throwaway database.
 */
export default defineConfig({
  testDir: './e2e-stack',
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    ...devices['Desktop Chrome'],
    baseURL: process.env['TRACKLY_URL'] ?? 'http://localhost:8080',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
