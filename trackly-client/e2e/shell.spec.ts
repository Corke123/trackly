import { expect, test } from './fixtures/board-api';

test.describe('the app shell', () => {
  test('shows the board name, who is signed in, and the board itself', async ({
    page,
    userBoard,
  }) => {
    await page.goto('/');

    await expect(page.getByTestId('board-name')).toHaveText(userBoard.name);
    await expect(page.getByTestId('user-menu')).toContainText('demo');
    await expect(page.getByTestId('swimlane-title-10')).toHaveText('To Do');
  });

  test('switches between the light and dark themes, and remembers the choice', async ({
    page,
    userBoard: _userBoard,
  }) => {
    await page.goto('/');

    const root = page.locator('html');
    await expect(root).toHaveClass(/trackly-light/);

    await page.getByTestId('theme-toggle').click();
    await expect(root).toHaveClass(/trackly-dark/);
    await expect(page.getByTestId('theme-toggle')).toHaveAttribute(
      'aria-label',
      'Switch to light theme',
    );

    // The choice has to survive a reload, or it is a gimmick rather than a preference.
    await page.reload();
    await expect(root).toHaveClass(/trackly-dark/);

    await page.getByTestId('theme-toggle').click();
    await expect(root).toHaveClass(/trackly-light/);
  });

  test('logs the user out', async ({ page, userBoard: _userBoard }) => {
    await page.goto('/');

    await page.getByTestId('user-menu').click();
    await page.getByTestId('logout').click();

    await expect(page.getByRole('heading', { name: 'Signed out' })).toBeVisible();
  });

  test('explains itself and offers a retry when the board cannot be loaded', async ({ page }) => {
    await page.route('**/api/me', (route) =>
      route.fulfill({ json: { username: 'demo', roles: ['ROLE_USER'], admin: false } }),
    );
    await page.route('**/api/boards', (route) =>
      route.fulfill({ status: 500, json: { detail: 'Board service is unavailable' } }),
    );

    await page.goto('/');

    await expect(page.getByTestId('board-error')).toContainText('Board service is unavailable');
    await expect(page.getByTestId('board-retry')).toBeVisible();
  });
});
