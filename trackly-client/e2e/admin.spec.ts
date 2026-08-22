import { expect, test } from './fixtures/board-api';
import { dragOnto } from './fixtures/drag';

test.describe('what an admin can do', () => {
  test.beforeEach(async ({ page, adminBoard: _adminBoard }) => {
    await page.goto('/');
    await expect(page.getByTestId('board')).toBeVisible();
  });

  test('renames the board', async ({ page, adminBoard }) => {
    await page.getByTestId('rename-board').click();
    await page.getByTestId('board-name-input').fill('Release board');
    await page.getByTestId('text-prompt-confirm').click();

    await expect(page.getByTestId('board-name')).toHaveText('Release board');
    expect(adminBoard.name).toBe('Release board');
  });

  test('adds a swimlane', async ({ page, adminBoard }) => {
    await page.getByTestId('add-swimlane').click();
    await page.getByTestId('swimlane-title-input').fill('Blocked');
    await page.getByTestId('text-prompt-confirm').click();

    await expect(page.getByText('Blocked', { exact: true })).toBeVisible();
    expect(adminBoard.swimlanes.map((lane) => lane.title)).toEqual([
      'To Do',
      'In Progress',
      'Done',
      'Blocked',
    ]);
  });

  test('deletes an empty swimlane once it has been confirmed', async ({ page, adminBoard }) => {
    await page.getByTestId('swimlane-menu-30').click();
    await page.getByTestId('delete-swimlane-30').click();
    await page.getByTestId('confirm-accept').click();

    await expect(page.getByTestId('swimlane-30')).toHaveCount(0);
    expect(adminBoard.swimlanes.map((lane) => lane.id)).toEqual([10, 20]);
  });

  test('keeps a swimlane when the deletion is not confirmed', async ({ page, adminBoard }) => {
    await page.getByTestId('swimlane-menu-30').click();
    await page.getByTestId('delete-swimlane-30').click();
    await page.getByTestId('confirm-cancel').click();

    await expect(page.getByTestId('swimlane-30')).toBeVisible();
    expect(adminBoard.swimlanes).toHaveLength(3);
  });

  test('cannot delete a swimlane that still holds tickets', async ({ page }) => {
    await page.getByTestId('swimlane-menu-10').click();

    await expect(page.getByTestId('delete-swimlane-10')).toBeDisabled();
  });

  test('deletes a ticket once it has been confirmed', async ({ page, adminBoard }) => {
    await page.getByTestId('ticket-menu-100').click();
    await page.getByTestId('delete-ticket-100').click();
    await page.getByTestId('confirm-accept').click();

    await expect(page.getByTestId('ticket-100')).toHaveCount(0);
    expect(adminBoard.titlesIn(10)).toEqual([]);
  });

  test('keeps a ticket when the deletion is not confirmed', async ({ page, adminBoard }) => {
    await page.getByTestId('ticket-menu-100').click();
    await page.getByTestId('delete-ticket-100').click();
    await page.getByTestId('confirm-cancel').click();

    await expect(page.getByTestId('ticket-100')).toBeVisible();
    expect(adminBoard.titlesIn(10)).toEqual(['Wire up the pipeline']);
  });

  test('reorders swimlanes from the menu', async ({ page, adminBoard }) => {
    await page.getByTestId('swimlane-menu-30').click();
    await page.getByTestId('swimlane-move-left-30').click();

    await expect(page.getByTestId('board').getByRole('heading', { level: 2 })).toHaveText([
      'To Do',
      'Done',
      'In Progress',
    ]);
    expect(adminBoard.swimlanes.map((lane) => lane.title)).toEqual([
      'To Do',
      'Done',
      'In Progress',
    ]);
  });

  test('reorders swimlanes by dragging one onto another', async ({ page, adminBoard }) => {
    await dragOnto(page, page.getByTestId('swimlane-handle-10'), page.getByTestId('swimlane-20'));

    await expect
      .poll(() => adminBoard.swimlanes.map((lane) => lane.title))
      .toEqual(['In Progress', 'To Do', 'Done']);
  });

  test('creates a ticket and assigns it in the same step', async ({ page, adminBoard }) => {
    await page.getByTestId('add-ticket-30').click();
    await page.getByTestId('ticket-title').fill('Ship it');
    await page.getByTestId('ticket-description').fill('Behind the approval gate');
    await page.getByTestId('ticket-assignee').click();
    await page.getByRole('option', { name: 'user' }).click();
    await page.getByTestId('ticket-submit').click();

    await expect(page.getByTestId('swimlane-30')).toContainText('Ship it');
    await expect(page.getByTestId('swimlane-30')).toContainText('user');

    const created = adminBoard.tickets.find((ticket) => ticket.title === 'Ship it');
    expect(created?.swimlaneId).toBe(30);
    expect(created?.assigneeId).toBe('user');
  });
});
