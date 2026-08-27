import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ActivityStreamService } from './activity-stream.service';
import { AuthService } from './auth.service';
import { BoardChange } from './board.models';
import { BoardStore } from './board.store';
import { LiveBoardService, REFRESH_WINDOW } from './live-board.service';

describe('LiveBoardService', () => {
  let boardChanges: Subject<BoardChange>;
  let reconnects: Subject<void>;
  let refresh: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers();
    boardChanges = new Subject<BoardChange>();
    reconnects = new Subject<void>();
    refresh = vi.fn().mockResolvedValue(undefined);

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: ActivityStreamService, useValue: { boardChanges, reconnects } },
        { provide: AuthService, useValue: { username: signal('admin') } },
        { provide: BoardStore, useValue: { refresh } },
      ],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function start(): void {
    TestBed.inject(LiveBoardService).start();
  }

  function settle(): void {
    vi.advanceTimersByTime(REFRESH_WINDOW);
  }

  it('refreshes the board when somebody else changes it', () => {
    start();

    boardChanges.next(change({ actorId: 'user' }));
    settle();

    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('leaves the board alone when the change is the signed-in user own doing', () => {
    start();

    boardChanges.next(change({ actorId: 'admin' }));
    settle();

    expect(refresh).not.toHaveBeenCalled();
  });

  it('asks for the board once when a burst of changes arrives', () => {
    start();

    boardChanges.next(change({ actorId: 'user' }));
    boardChanges.next(change({ actorId: 'user' }));
    boardChanges.next(change({ actorId: 'user' }));
    settle();

    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('refreshes again once the next window opens', () => {
    start();

    boardChanges.next(change({ actorId: 'user' }));
    settle();
    boardChanges.next(change({ actorId: 'user' }));
    settle();

    expect(refresh).toHaveBeenCalledTimes(2);
  });

  it('catches up on whatever was missed when the stream comes back', () => {
    start();

    reconnects.next();
    settle();

    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('listens once however many times it is started', () => {
    start();
    start();

    boardChanges.next(change({ actorId: 'user' }));
    settle();

    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('stops listening once the injector is destroyed', () => {
    start();
    TestBed.resetTestingModule();

    boardChanges.next(change({ actorId: 'user' }));
    settle();

    expect(refresh).not.toHaveBeenCalled();
  });
});

function change(overrides: Partial<BoardChange>): BoardChange {
  return {
    boardId: 1,
    type: 'TicketMoved',
    actorId: 'user',
    occurredAt: '2026-07-25T10:00:00Z',
    ...overrides,
  };
}
