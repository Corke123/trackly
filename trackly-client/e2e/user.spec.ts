import { expect, test } from './fixtures/board-api';
import { dragOnto } from './fixtures/drag';

test.describe('what a plain user can do', () => {
  test.beforeEach(async ({ page, userBoard: _userBoard }) => {
    await page.goto('/');
    await expect(page.getByTestId('board')).toBeVisible();
  });

  test('is offered none of the board-shaping controls', async ({ page }) => {
    await expect(page.getByTestId('rename-board')).toHaveCount(0);
    await expect(page.getByTestId('add-swimlane')).toHaveCount(0);
    await expect(page.getByTestId('swimlane-menu-10')).toHaveCount(0);
    await expect(page.getByTestId('swimlane-handle-10')).toHaveCount(0);
  });

  test('is not offered the delete action on a ticket', async ({ page }) => {
    await page.getByTestId('ticket-menu-100').click();

    await expect(page.getByTestId('assign-ticket-100')).toBeVisible();
    await expect(page.getByTestId('delete-ticket-100')).toHaveCount(0);
  });

  test('adds a ticket', async ({ page, userBoard }) => {
    await page.getByTestId('add-ticket-10').click();
    await page.getByTestId('ticket-title').fill('Fix the flaky test');
    await page.getByTestId('ticket-submit').click();

    await expect(page.getByTestId('swimlane-10')).toContainText('Fix the flaky test');
    expect(userBoard.titlesIn(10)).toContain('Fix the flaky test');
  });

  test('assigns an existing ticket', async ({ page, userBoard }) => {
    await page.getByTestId('ticket-menu-100').click();
    await page.getByTestId('assign-ticket-100').click();
    await page.getByTestId('assignee-select').click();
    await page.getByRole('option', { name: 'demo' }).click();
    await page.getByTestId('assign-submit').click();

    await expect(page.getByTestId('ticket-assignee-100')).toContainText('demo');
    expect(userBoard.tickets.find((ticket) => ticket.id === 100)?.assigneeId).toBe('demo');
  });

  test('moves a ticket to another swimlane from its menu', async ({ page, userBoard }) => {
    await page.getByTestId('ticket-menu-100').click();
    await page.getByTestId('move-ticket-menu-100').click();
    await page.getByTestId('move-ticket-100-to-20').click();

    await expect(page.getByTestId('swimlane-20')).toContainText('Wire up the pipeline');
    await expect(page.getByTestId('swimlane-10')).not.toContainText('Wire up the pipeline');
    expect(userBoard.titlesIn(20)).toEqual(['Wire up the pipeline', 'Write the ADR']);
  });

  test('drags a ticket into another swimlane', async ({ page, userBoard }) => {
    await dragOnto(page, page.getByTestId('ticket-100'), page.getByTestId('swimlane-30'), {
      grabOffsetY: 10,
    });

    await expect.poll(() => userBoard.titlesIn(30)).toEqual(['Wire up the pipeline']);
    await expect(page.getByTestId('swimlane-count-30')).toHaveText('1');
    await expect(page.getByTestId('swimlane-count-10')).toHaveText('0');
  });

  test('sees the ticket move back when the board refuses it', async ({ page }) => {
    await page.route('**/api/tickets/100', (route) =>
      route.fulfill({ status: 422, json: { detail: 'Swimlane 30 not on the board 1' } }),
    );

    await page.getByTestId('ticket-menu-100').click();
    await page.getByTestId('move-ticket-menu-100').click();
    await page.getByTestId('move-ticket-100-to-30').click();

    await expect(page.getByText('Swimlane 30 not on the board 1')).toBeVisible();
    await expect(page.getByTestId('swimlane-10')).toContainText('Wire up the pipeline');
  });
});
