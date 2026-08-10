import { expect, test } from './fixtures/board-api';

test.describe('live board notifications', () => {
  test('pops up what somebody else did to your ticket, in the top-right corner', async ({
    page,
    userBoard: _userBoard,
    activityStream,
  }) => {
    activityStream.push('admin assigned "Write the ADR" to you');

    await page.goto('/');

    const announcement = page.locator('.app-announcement');
    await expect(announcement).toContainText('admin assigned "Write the ADR" to you');

    const popup = await announcement.boundingBox();
    const viewport = page.viewportSize();
    expect(popup).not.toBeNull();
    expect(viewport).not.toBeNull();
    expect(popup!.y).toBeLessThan(viewport!.height / 2);
    expect(popup!.x + popup!.width / 2).toBeGreaterThan(viewport!.width / 2);
  });

  test('pops up a move of your ticket while you are looking at the board', async ({
    page,
    userBoard: _userBoard,
    activityStream,
  }) => {
    await page.goto('/');
    await expect(page.getByTestId('swimlane-title-10')).toBeVisible();
    await expect(page.locator('.app-announcement')).toHaveCount(0);

    activityStream.push('admin moved your ticket "Write the ADR" to Done', 'TicketMoved');

    await expect(page.locator('.app-announcement')).toContainText(
      'admin moved your ticket "Write the ADR" to Done',
    );
  });

  test('says nothing when nothing is addressed to you', async ({
    page,
    userBoard: _userBoard,
    activityStream: _activityStream,
  }) => {
    await page.goto('/');
    await expect(page.getByTestId('swimlane-title-10')).toBeVisible();

    await expect(page.locator('.app-announcement')).toHaveCount(0);
  });

  test('dismisses an announcement when asked', async ({
    page,
    userBoard: _userBoard,
    activityStream,
  }) => {
    activityStream.push('admin assigned "Write the ADR" to you');

    await page.goto('/');

    const announcement = page.locator('.app-announcement');
    await expect(announcement).toBeVisible();

    await announcement.getByRole('button', { name: 'Dismiss' }).click();

    await expect(announcement).toHaveCount(0);
  });
});
