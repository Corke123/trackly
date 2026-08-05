import { defineConfig } from 'vitest/config';

/**
 * The client's coverage gate, the counterpart of the JaCoCo gates the services are held to
 * (ADR 0010): a merge is blocked when tests stop keeping up with the code, not merely reported on.
 * The thresholds sit below what the suite currently reaches, so they catch a slide rather than
 * every ordinary edit.
 */
export default defineConfig({
  test: {
    coverage: {
      provider: 'v8',
      reportsDirectory: 'coverage',
      thresholds: {
        statements: 85,
        branches: 85,
        functions: 85,
        lines: 85,
      },
      exclude: [
        // Bootstrap and wiring: exercised by the end-to-end journeys, not by unit tests.
        'src/main.ts',
        'src/app/app.config.ts',
        'src/app/app.routes.ts',
        'src/testing/**',
        'e2e/**',
        '**/*.config.ts',
      ],
    },
  },
});
