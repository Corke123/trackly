import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { THEME_STORAGE_KEY, ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.className = '';
    document.documentElement.style.colorScheme = '';
    stubSystemPreference(false);
  });

  function create(): ThemeService {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);
    TestBed.tick();
    return service;
  }

  it('follows the operating system until the user says otherwise', () => {
    stubSystemPreference(true);

    const service = create();

    expect(service.preference()).toBe('system');
    expect(service.resolved()).toBe('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');
  });

  it('switches the page to dark when toggled from light', () => {
    const service = create();

    service.toggle();
    TestBed.tick();

    expect(service.resolved()).toBe('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');
    expect(document.documentElement.classList.contains('trackly-dark')).toBe(true);
    expect(document.documentElement.classList.contains('trackly-light')).toBe(false);
  });

  it('switches back to light when toggled again', () => {
    const service = create();

    service.toggle();
    TestBed.tick();
    service.toggle();
    TestBed.tick();

    expect(service.resolved()).toBe('light');
    expect(document.documentElement.classList.contains('trackly-light')).toBe(true);
  });

  it('remembers the choice for the next visit', () => {
    const service = create();

    service.toggle();
    TestBed.tick();

    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(create().resolved()).toBe('dark');
  });

  it('ignores a stored value it does not recognise', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'neon');

    expect(create().preference()).toBe('system');
  });

  it('keeps working when storage is unavailable', () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied');
    });
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('denied');
    });
    try {
      const service = create();
      service.set('dark');
      TestBed.tick();

      expect(service.resolved()).toBe('dark');
    } finally {
      getItem.mockRestore();
      setItem.mockRestore();
    }
  });
});

/**
 * jsdom has no real color-scheme preference, so the query the service reads is stubbed on the
 * document it is given.
 */
function stubSystemPreference(prefersDark: boolean): void {
  const matchMedia = vi.fn().mockReturnValue({
    matches: prefersDark,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  });
  Object.defineProperty(window, 'matchMedia', { value: matchMedia, configurable: true });
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [{ provide: DOCUMENT, useValue: document }] });
}
