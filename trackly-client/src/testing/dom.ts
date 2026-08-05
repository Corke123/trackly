import { ComponentFixture } from '@angular/core/testing';

/**
 * Every spec reaches for elements the same way the end-to-end journeys do — by `data-test-id`, so a
 * template change that breaks one breaks both, rather than only the slower suite.
 */
export function query(fixture: ComponentFixture<unknown>, testId: string): HTMLElement | null {
  const element = fixture.nativeElement as HTMLElement;
  return element.querySelector<HTMLElement>(`[data-testid="${testId}"]`);
}

export function click(fixture: ComponentFixture<unknown>, testId: string): void {
  query(fixture, testId)?.click();
}

/** Menus and dialogs render into an overlay attached to the body, outside the fixture's element. */
export function queryOverlay(testId: string): HTMLElement | null {
  return document.querySelector<HTMLElement>(`[data-testid="${testId}"]`);
}

export function clickOverlay(testId: string): void {
  queryOverlay(testId)?.click();
}

export function type(fixture: ComponentFixture<unknown>, testId: string, value: string): void {
  const input = query(fixture, testId);
  if (!(input instanceof HTMLInputElement) && !(input instanceof HTMLTextAreaElement)) {
    throw new Error(`No input field with data-test-id "${testId}"`);
  }
  input.value = value;
  input.dispatchEvent(new Event('input'));
}
