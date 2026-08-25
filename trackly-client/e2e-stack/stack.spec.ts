import { Page, expect, test } from '@playwright/test';

test.describe.configure({ timeout: 180_000 });

test('an admin signs in and shapes the real board', async ({ page }) => {
  const runId = Date.now().toString(36);
  const ticketTitle = `Smoke test ticket ${runId}`;
  const laneTitle = `Blocked ${runId}`;

  await signIn(page, 'admin', 'admin');

  await expect(page.getByTestId('rename-board')).toBeVisible();
  await expect(page.getByTestId('add-swimlane')).toBeVisible();

  await page.locator('[data-testid^="add-ticket-"]').first().click();
  await page.getByRole('dialog').getByTestId('ticket-title').fill(ticketTitle);
  await page.getByRole('dialog').getByTestId('ticket-assignee').click();
  await page.getByRole('option', { name: 'demo' }).click();
  await page.getByRole('dialog').getByTestId('ticket-submit').click();
  await expect(ticketCard(page, ticketTitle)).toBeVisible({ timeout: 20_000 });

  await settle(page);
  await page.getByTestId('rename-board').click();
  await dialogField(page, 'board-name-input').fill('Thesis board');
  await confirmDialog(page);
  await expect(page.getByTestId('board-name')).toHaveText('Thesis board', { timeout: 20_000 });

  await settle(page);
  await page.getByTestId('add-swimlane').click();
  await dialogField(page, 'swimlane-title-input').fill(laneTitle);
  await confirmDialog(page);
  await expect(swimlane(page, laneTitle)).toBeVisible({ timeout: 20_000 });

  // A reload is what separates a persisted change from an optimistic one.
  await page.reload();
  await expect(page.getByTestId('board-name')).toHaveText('Thesis board', { timeout: 60_000 });
  await expect(ticketCard(page, ticketTitle)).toBeVisible();
  await expect(swimlane(page, laneTitle)).toBeVisible();

  await settle(page);
  await deleteTicket(page, ticketTitle);
  await expect(ticketCard(page, ticketTitle)).toHaveCount(0, { timeout: 20_000 });

  await settle(page);
  await deleteSwimlane(page, laneTitle);
  await expect(swimlane(page, laneTitle)).toHaveCount(0, { timeout: 20_000 });

  await page.reload();
  await expect(page.getByTestId('board')).toBeVisible({ timeout: 60_000 });
  await expect(ticketCard(page, ticketTitle)).toHaveCount(0);
  await expect(swimlane(page, laneTitle)).toHaveCount(0);
});

test('a plain user gets no admin controls, and the service refuses them anyway', async ({
  page,
}) => {
  const ticketTitle = `User ticket ${Date.now().toString(36)}`;

  await signIn(page, 'user', 'user');

  await expect(page.getByTestId('rename-board')).toHaveCount(0);
  await expect(page.getByTestId('add-swimlane')).toHaveCount(0);

  // Hiding the controls proves nothing on its own — ask board-service directly with this session.
  const csrf = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? '';
  const status = await page.evaluate(
    async ([token]) => {
      const response = await fetch('/api/boards/1', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
        body: JSON.stringify({ name: 'Hijacked' }),
      });
      return response.status;
    },
    [csrf],
  );
  expect(status).toBe(403);

  // What this role may do still works.
  await page.locator('[data-testid^="add-ticket-"]').first().click();
  await page.getByRole('dialog').getByTestId('ticket-title').fill(ticketTitle);
  await page.getByRole('dialog').getByTestId('ticket-submit').click();
  await expect(ticketCard(page, ticketTitle)).toBeVisible({ timeout: 20_000 });

  await page.context().clearCookies();
  await signIn(page, 'admin', 'admin');

  await settle(page);
  await deleteTicket(page, ticketTitle);
  await expect(ticketCard(page, ticketTitle)).toHaveCount(0, { timeout: 20_000 });

  await page.reload();
  await expect(page.getByTestId('board')).toBeVisible({ timeout: 60_000 });
  await expect(ticketCard(page, ticketTitle)).toHaveCount(0);
});

/**
 * The real sign-in: identity-service's form login, then the consent screen the authorization server
 * shows the first time an account approves the `trackly` client. Consent is remembered afterwards,
 * so the screen is handled if it appears rather than assumed.
 */
async function signIn(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/');

  await page.waitForSelector('input[name="username"]', { timeout: 60_000 });
  await page.fill('input[name="username"]', username);
  await page.fill('input[name="password"]', password);
  await page.click('button[type="submit"]');

  const consentHeading = page.getByText('Consent required');
  await Promise.race([
    consentHeading.waitFor({ timeout: 30_000 }).catch(() => undefined),
    page.getByTestId('board').waitFor({ timeout: 30_000 }).catch(() => undefined),
  ]);

  if (await consentHeading.count()) {
    for (const box of await page.locator('input[type="checkbox"]').all()) {
      await box.check();
    }
    await page.getByRole('button', { name: /submit consent/i }).click();
  }

  await expect(page.getByTestId('board')).toBeVisible({ timeout: 60_000 });
}

function ticketCard(page: Page, title: string) {
  return page.getByRole('article', { name: title });
}

function swimlane(page: Page, title: string) {
  return page.getByRole('region', { name: `${title} swimlane` });
}

async function deleteTicket(page: Page, title: string): Promise<void> {
  await ticketCard(page, title)
    .getByRole('button', { name: `Actions for ${title}` })
    .click();
  await page.getByRole('menuitem', { name: 'Delete ticket' }).click();
  await page.getByRole('dialog').getByTestId('confirm-accept').last().click();
}

async function deleteSwimlane(page: Page, title: string): Promise<void> {
  await swimlane(page, title)
    .getByRole('button', { name: `Actions for ${title}` })
    .click();
  await page.getByRole('menuitem', { name: 'Delete swimlane' }).click();
  await page.getByRole('dialog').getByTestId('confirm-accept').last().click();
}

/** Opening a dialog while the previous one is still fading out leaves both in the DOM. */
function dialogField(page: Page, testId: string) {
  return page.getByRole('dialog').getByTestId(testId).last();
}

function confirmDialog(page: Page) {
  return page.getByRole('dialog').getByTestId('text-prompt-confirm').last().click();
}

async function settle(page: Page): Promise<void> {
  await page.waitForTimeout(500);
}
