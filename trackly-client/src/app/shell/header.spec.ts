import { ANIMATION_MODULE_TYPE, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clickOverlay, query } from '../../testing/dom';
import { provideTracklyIcons } from '../core/icons';
import { AuthService } from '../core/auth.service';
import { ThemeService } from '../core/theme.service';
import { Header } from './header';

describe('Header', () => {
  const logout = vi.fn();
  const toggle = vi.fn();
  let resolvedTheme: ReturnType<typeof signal<'light' | 'dark'>>;
  let fixture: ComponentFixture<Header>;

  beforeEach(async () => {
    logout.mockClear();
    toggle.mockClear();
    resolvedTheme = signal<'light' | 'dark'>('light');

    await TestBed.configureTestingModule({
      imports: [Header],
      providers: [
        provideZonelessChangeDetection(),
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        provideTracklyIcons(),
        {
          provide: AuthService,
          useValue: { username: signal('demo'), isAdmin: signal(false), logout },
        },
        { provide: ThemeService, useValue: { resolved: resolvedTheme, toggle } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Header);
    fixture.componentRef.setInput('boardName', 'Trackly Board');
    await fixture.whenStable();
  });

  it('shows the board name as the app title', () => {
    expect(query(fixture, 'board-name')?.textContent?.trim()).toBe('Trackly Board');
  });

  it('shows who is signed in', () => {
    expect(fixture.nativeElement.textContent).toContain('demo');
  });

  it('offers no rename control to someone who may not rename the board', () => {
    expect(query(fixture, 'rename-board')).toBeNull();
  });

  it('offers the rename control to an admin, and asks for a rename when it is used', async () => {
    const rename = vi.fn();
    fixture.componentInstance.rename.subscribe(rename);
    fixture.componentRef.setInput('canRename', true);
    await fixture.whenStable();

    query(fixture, 'rename-board')?.click();

    expect(rename).toHaveBeenCalled();
  });

  it('offers to switch to dark while the light theme is showing', () => {
    expect(query(fixture, 'theme-toggle')?.getAttribute('aria-label')).toBe('Switch to dark theme');
  });

  it('offers to switch back to light once the dark theme is showing', async () => {
    resolvedTheme.set('dark');
    await fixture.whenStable();

    expect(query(fixture, 'theme-toggle')?.getAttribute('aria-label')).toBe('Switch to light theme');
  });

  it('switches the theme when the toggle is used', () => {
    query(fixture, 'theme-toggle')?.click();

    expect(toggle).toHaveBeenCalled();
  });

  it('logs the user out from the user menu', async () => {
    query(fixture, 'user-menu')?.click();
    await fixture.whenStable();

    clickOverlay('logout');

    expect(logout).toHaveBeenCalled();
  });
});
