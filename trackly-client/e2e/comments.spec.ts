import { expect, test } from './fixtures/board-api';

async function openThread(page: import('@playwright/test').Page, ticketId: number): Promise<void> {
  await page.goto('/');
  await page.getByTestId(`ticket-menu-${ticketId}`).click();
  await page.getByTestId(`open-ticket-${ticketId}`).click();
  await expect(page.getByTestId('ticket-detail')).toBeVisible();
}

test.describe('talking about a ticket', () => {
  test('leaves a comment and sees it in the thread', async ({ page, userBoard }) => {
    await openThread(page, 100);
    await expect(page.getByTestId('comments-empty')).toBeVisible();

    await page.getByTestId('comment-input').fill('Blocked on the gateway route');
    await page.getByTestId('comment-submit').click();

    await expect(page.getByTestId('ticket-detail')).toContainText('Blocked on the gateway route');
    expect(userBoard.commentsOn(100).map((comment) => comment.body)).toEqual([
      'Blocked on the gateway route',
    ]);
  });

  test('deletes a comment it wrote itself', async ({ page, userBoard }) => {
    const comment = userBoard.postComment(100, 'demo', 'Never mind');
    await openThread(page, 100);
    await expect(page.getByTestId(`comment-${comment?.id}`)).toBeVisible();

    await page.getByTestId(`delete-comment-${comment?.id}`).click();

    await expect(page.getByTestId(`comment-${comment?.id}`)).toHaveCount(0);
    expect(userBoard.commentsOn(100)).toHaveLength(0);
  });

  test("is offered no delete on somebody else's comment", async ({ page, userBoard }) => {
    const comment = userBoard.postComment(100, 'admin', 'The admin wrote this');
    await openThread(page, 100);

    await expect(page.getByTestId(`comment-${comment?.id}`)).toBeVisible();
    await expect(page.getByTestId(`delete-comment-${comment?.id}`)).toHaveCount(0);
  });

  test("may delete anybody's comment as an admin", async ({ page, adminBoard }) => {
    const comment = adminBoard.postComment(100, 'demo', 'A plain user wrote this');
    await openThread(page, 100);

    await page.getByTestId(`delete-comment-${comment?.id}`).click();

    await expect(page.getByTestId(`comment-${comment?.id}`)).toHaveCount(0);
  });

  test('picks up a comment somebody else left, without a reload', async ({
    page,
    userBoard,
    activityStream,
  }) => {
    await openThread(page, 100);
    await expect(page.getByTestId('comments-empty')).toBeVisible();

    userBoard.postComment(100, 'admin', 'Left this while you were reading');
    activityStream.pushBoardChange('TicketCommented', 'admin');

    await expect(page.getByTestId('ticket-detail')).toContainText(
      'Left this while you were reading',
    );
  });
});
