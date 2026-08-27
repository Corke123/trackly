import { expect, test } from './fixtures/board-api';

test.describe('a board that keeps up with the rest of the team', () => {
  test('shows a ticket somebody else moved, without a reload', async ({
    page,
    userBoard,
    activityStream,
  }) => {
    await page.goto('/');
    await expect(page.getByTestId('swimlane-10')).toContainText('Wire up the pipeline');

    userBoard.moveTicket(100, 30, 0);
    activityStream.pushBoardChange();

    await expect(page.getByTestId('swimlane-30')).toContainText('Wire up the pipeline');
    await expect(page.getByTestId('swimlane-10')).not.toContainText('Wire up the pipeline');
  });
});
