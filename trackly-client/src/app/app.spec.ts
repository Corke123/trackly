import { ANIMATION_MODULE_TYPE, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { click, query } from '../testing/dom';
import { App } from './app';
import { ActivityStreamService } from './core/activity-stream.service';
import { AuthService } from './core/auth.service';
import { ActivityNotification } from './core/board.models';
import { BoardStore } from './core/board.store';
import { LiveBoardService } from './core/live-board.service';
import { provideTracklyIcons } from './core/icons';
import { NotificationService } from './core/notification.service';
import { ThemeService } from './core/theme.service';

describe('App', () => {
  let load: ReturnType<typeof vi.fn>;
  let renameBoard: ReturnType<typeof vi.fn>;
  let loadUser: ReturnType<typeof vi.fn>;
  let isAdmin: ReturnType<typeof signal<boolean>>;
  let dialogResult: string | undefined;
  let notifications: Subject<ActivityNotification>;
  let connect: ReturnType<typeof vi.fn>;
  let startLiveBoard: ReturnType<typeof vi.fn>;
  let announce: ReturnType<typeof vi.fn>;

  async function build(): Promise<ComponentFixture<App>> {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideZonelessChangeDetection(),
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        provideTracklyIcons(),
        provideRouter([]),
        {
          provide: BoardStore,
          useValue: { load, renameBoard, boardName: signal('Trackly Board') },
        },
        {
          provide: AuthService,
          useValue: { load: loadUser, isAdmin, username: signal('admin'), logout: vi.fn() },
        },
        {
          provide: ThemeService,
          useValue: { resolved: signal<'light' | 'dark'>('light'), toggle: vi.fn() },
        },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(dialogResult) }) } },
        {
          provide: ActivityStreamService,
          useValue: { notifications, connect, disconnect: vi.fn() },
        },
        { provide: LiveBoardService, useValue: { start: startLiveBoard } },
        {
          provide: NotificationService,
          useValue: { announce, notify: vi.fn(), reportError: vi.fn() },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return fixture;
  }

  beforeEach(() => {
    load = vi.fn().mockResolvedValue(undefined);
    renameBoard = vi.fn().mockResolvedValue(undefined);
    loadUser = vi.fn().mockResolvedValue(undefined);
    isAdmin = signal(true);
    dialogResult = undefined;
    notifications = new Subject<ActivityNotification>();
    connect = vi.fn();
    startLiveBoard = vi.fn();
    announce = vi.fn();
  });

  it('asks for the current user and the board together rather than in a chain', async () => {
    await build();

    expect(loadUser).toHaveBeenCalled();
    expect(load).toHaveBeenCalled();
  });

  it('still loads the board when the current user cannot be resolved', async () => {
    loadUser.mockRejectedValue(new Error('401'));

    await build();

    expect(load).toHaveBeenCalled();
  });

  it('shows the board name in the header', async () => {
    const fixture = await build();

    expect(query(fixture, 'board-name')?.textContent?.trim()).toBe('Trackly Board');
  });

  it('renames the board when an admin uses the header control', async () => {
    dialogResult = 'Release board';
    const fixture = await build();

    click(fixture, 'rename-board');
    await fixture.whenStable();

    expect(renameBoard).toHaveBeenCalledWith('Release board');
  });

  it('renames nothing when the dialog is dismissed', async () => {
    dialogResult = undefined;
    const fixture = await build();

    click(fixture, 'rename-board');
    await fixture.whenStable();

    expect(renameBoard).not.toHaveBeenCalled();
  });

  it('offers no rename control to a plain user', async () => {
    isAdmin.set(false);
    const fixture = await build();

    expect(query(fixture, 'rename-board')).toBeNull();
  });

  it('opens the activity stream as the shell starts', async () => {
    await build();

    expect(connect).toHaveBeenCalled();
  });

  it('starts following what other users do to the board as the shell starts', async () => {
    await build();

    expect(startLiveBoard).toHaveBeenCalled();
  });

  it('announces board news addressed to the signed-in user', async () => {
    await build();

    notifications.next(activity('admin moved your ticket "Fix login" to Doing'));

    expect(announce).toHaveBeenCalledWith('admin moved your ticket "Fix login" to Doing');
  });

  it('stops announcing once the shell is destroyed', async () => {
    const fixture = await build();
    fixture.destroy();

    notifications.next(activity('admin assigned "Fix login" to you'));

    expect(announce).not.toHaveBeenCalled();
  });
});

function activity(message: string): ActivityNotification {
  return {
    id: 1,
    boardId: 1,
    type: 'TicketMoved',
    message,
    actorId: 'admin',
    occurredAt: '2026-07-25T10:00:00Z',
  };
}
