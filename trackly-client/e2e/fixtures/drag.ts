import { Locator, Page } from '@playwright/test';

/**
 * The CDK starts a drag from pointer events, so a move has to be made the way a hand makes it: press,
 * travel far enough to pass the drag threshold, release. Playwright's `dragTo` presses and releases
 * without the travel in between, which the CDK reads as a click.
 */
export async function dragOnto(
  page: Page,
  from: Locator,
  to: Locator,
  options: { readonly grabOffsetY?: number } = {},
): Promise<void> {
  const source = await from.boundingBox();
  const target = await to.boundingBox();
  if (!source || !target) {
    throw new Error('Cannot drag: one of the elements is not on screen');
  }

  // Cards are grabbed near their top edge so the pointer starts on the card rather than on a
  // control inside it; a lane is grabbed by the middle of its drag handle.
  const startX = source.x + source.width / 2;
  const startY = source.y + (options.grabOffsetY ?? source.height / 2);
  const endX = target.x + target.width / 2;
  const endY = target.y + target.height / 2;

  await page.mouse.move(startX, startY);
  await page.mouse.down();
  for (let step = 1; step <= STEPS; step++) {
    await page.mouse.move(
      startX + ((endX - startX) * step) / STEPS,
      startY + ((endY - startY) * step) / STEPS,
    );
  }
  await page.mouse.up();
}

const STEPS = 10;
