import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';

export type ThemePreference = 'light' | 'dark' | 'system';

export const THEME_STORAGE_KEY = 'trackly.theme';

/**
 * Switches the whole app between light and dark by writing the CSS `color-scheme` property, which
 * is what the Material theme in `theme.scss` resolves its `light-dark()` tokens against.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);

  private readonly systemPrefersDark = signal(this.readSystemPreference());

  readonly preference = signal<ThemePreference>(this.readStoredPreference());

  /** What the user actually sees, once 'system' has been resolved. */
  readonly resolved = computed<'light' | 'dark'>(() => {
    const preference = this.preference();
    if (preference === 'system') {
      return this.systemPrefersDark() ? 'dark' : 'light';
    }
    return preference;
  });

  constructor() {
    this.watchSystemPreference();

    effect(() => {
      const resolved = this.resolved();
      const root = this.document.documentElement;
      root.style.colorScheme = resolved;
      root.classList.toggle('trackly-dark', resolved === 'dark');
      root.classList.toggle('trackly-light', resolved === 'light');
    });

    effect(() => this.storePreference(this.preference()));
  }

  /** Flips between light and dark, resolving 'system' to the opposite of what is on screen. */
  toggle(): void {
    this.preference.set(this.resolved() === 'dark' ? 'light' : 'dark');
  }

  set(preference: ThemePreference): void {
    this.preference.set(preference);
  }

  private watchSystemPreference(): void {
    const query = this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)');
    query?.addEventListener?.('change', (event) => this.systemPrefersDark.set(event.matches));
  }

  private readSystemPreference(): boolean {
    return this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)')?.matches ?? false;
  }

  private readStoredPreference(): ThemePreference {
    // Storage is unavailable, or throws on every call, in some privacy modes — and a remembered
    // theme is never worth failing the app's startup for.
    try {
      const stored = this.document.defaultView?.localStorage?.getItem(THEME_STORAGE_KEY);
      return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system';
    } catch {
      return 'system';
    }
  }

  private storePreference(preference: ThemePreference): void {
    try {
      this.document.defaultView?.localStorage?.setItem(THEME_STORAGE_KEY, preference);
    } catch {
      // See readStoredPreference.
    }
  }
}
