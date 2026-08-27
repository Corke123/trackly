import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ACTIVITY_EVENT,
  BOARD_CHANGED_EVENT,
  ActivityStreamService,
  EVENT_SOURCE_FACTORY,
  RECONNECT_MAX_DELAY,
  RECONNECT_MIN_DELAY,
} from './activity-stream.service';
import { API_BASE_URL } from './api.config';
import { ActivityNotification, BoardChange } from './board.models';

describe('ActivityStreamService', () => {
  let opened: FakeEventSource[];

  beforeEach(() => {
    vi.useFakeTimers();
    opened = [];
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: API_BASE_URL, useValue: '/api' },
        {
          provide: EVENT_SOURCE_FACTORY,
          useValue: (url: string) => {
            const source = new FakeEventSource(url);
            opened.push(source);
            return source as unknown as EventSource;
          },
        },
      ],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function connect(): ActivityStreamService {
    const service = TestBed.inject(ActivityStreamService);
    service.connect();
    return service;
  }

  function received(service: ActivityStreamService): ActivityNotification[] {
    const seen: ActivityNotification[] = [];
    service.notifications.subscribe((notification) => seen.push(notification));
    return seen;
  }

  it('opens the stream against the gateway', () => {
    connect();

    expect(opened).toHaveLength(1);
    expect(opened[0].url).toBe('/api/activity/stream');
  });

  it('opens one stream however many times it is asked', () => {
    const service = connect();
    service.connect();

    expect(opened).toHaveLength(1);
  });

  it('emits the notifications that arrive on the stream', () => {
    const service = connect();
    const seen = received(service);

    opened[0].emit(
      ACTIVITY_EVENT,
      notification({ id: 1, message: 'admin assigned "Fix login" to you' }),
    );

    expect(seen).toHaveLength(1);
    expect(seen[0].message).toBe('admin assigned "Fix login" to you');
  });

  it('replays nothing to a late subscriber', () => {
    const service = connect();
    opened[0].emit(ACTIVITY_EVENT, notification({ id: 1 }));

    const seen = received(service);

    expect(seen).toHaveLength(0);
  });

  it('drops a frame that is not a notification rather than throwing', () => {
    const service = connect();
    const seen = received(service);

    opened[0].emit(ACTIVITY_EVENT, 'not json at all');
    opened[0].emit(ACTIVITY_EVENT, JSON.stringify({ unexpected: true }));
    opened[0].emit(ACTIVITY_EVENT, notification({ id: 3 }));

    expect(seen.map((entry) => entry.id)).toEqual([3]);
  });

  it('emits the board changes that arrive on the stream', () => {
    const service = connect();
    const seen = changesOf(service);

    opened[0].emit(BOARD_CHANGED_EVENT, boardChange({ actorId: 'user' }));

    expect(seen).toHaveLength(1);
    expect(seen[0].actorId).toBe('user');
  });

  it('drops a frame that is not a board change rather than throwing', () => {
    const service = connect();
    const seen = changesOf(service);

    opened[0].emit(BOARD_CHANGED_EVENT, 'not json at all');
    opened[0].emit(BOARD_CHANGED_EVENT, JSON.stringify({ unexpected: true }));
    opened[0].emit(BOARD_CHANGED_EVENT, boardChange({ boardId: 3 }));

    expect(seen.map((entry) => entry.boardId)).toEqual([3]);
  });

  it('never resumes from a board change, which carries no id of its own', () => {
    connect();
    opened[0].emit(BOARD_CHANGED_EVENT, boardChange({}));

    fail(opened[0]);
    vi.advanceTimersByTime(RECONNECT_MIN_DELAY);

    expect(opened[1].url).toBe('/api/activity/stream');
  });

  it('says nothing about the first time the stream opens', () => {
    const service = connect();
    const reconnects = reconnectsOf(service);

    opened[0].emit('open', '');

    expect(reconnects).toHaveLength(0);
  });

  it('reports a stream that opened again, since board changes are not replayed', () => {
    const service = connect();
    const reconnects = reconnectsOf(service);

    opened[0].emit('open', '');
    fail(opened[0]);
    vi.advanceTimersByTime(RECONNECT_MIN_DELAY);
    opened[1].emit('open', '');

    expect(reconnects).toHaveLength(1);
  });

  it('leaves a stream the browser is retrying by itself alone', () => {
    connect();

    opened[0].readyState = FakeEventSource.CONNECTING;
    opened[0].emit('error', '');
    vi.advanceTimersByTime(RECONNECT_MAX_DELAY);

    expect(opened).toHaveLength(1);
  });

  it('reopens a stream the browser has given up on, backing off as it keeps failing', () => {
    connect();

    fail(opened[0]);
    vi.advanceTimersByTime(RECONNECT_MIN_DELAY);
    expect(opened).toHaveLength(2);

    fail(opened[1]);
    vi.advanceTimersByTime(RECONNECT_MIN_DELAY);
    expect(opened).toHaveLength(2);

    vi.advanceTimersByTime(RECONNECT_MIN_DELAY);
    expect(opened).toHaveLength(3);
  });

  it('starts backing off again from scratch once a stream has opened', () => {
    connect();

    fail(opened[0]);
    vi.advanceTimersByTime(RECONNECT_MIN_DELAY);
    opened[1].emit('open', '');

    fail(opened[1]);
    vi.advanceTimersByTime(RECONNECT_MIN_DELAY);

    expect(opened).toHaveLength(3);
  });

  it('asks to resume from the last notification it saw when it reopens the stream itself', () => {
    connect();
    opened[0].emit(ACTIVITY_EVENT, notification({ id: 12 }));

    fail(opened[0]);
    vi.advanceTimersByTime(RECONNECT_MIN_DELAY);

    expect(opened[1].url).toBe('/api/activity/stream?lastEventId=12');
  });

  it('closes the stream and cancels any pending retry when disconnected', () => {
    const service = connect();

    fail(opened[0]);
    service.disconnect();
    vi.advanceTimersByTime(RECONNECT_MAX_DELAY);

    expect(opened).toHaveLength(1);
    expect(opened[0].closed).toBe(true);
  });

  function changesOf(service: ActivityStreamService): BoardChange[] {
    const seen: BoardChange[] = [];
    service.boardChanges.subscribe((change) => seen.push(change));
    return seen;
  }

  function reconnectsOf(service: ActivityStreamService): unknown[] {
    const seen: unknown[] = [];
    service.reconnects.subscribe(() => seen.push(true));
    return seen;
  }

  function fail(source: FakeEventSource): void {
    source.readyState = FakeEventSource.CLOSED;
    source.emit('error', '');
  }
});

function notification(overrides: Partial<ActivityNotification>): string {
  return JSON.stringify({
    id: 1,
    boardId: 1,
    type: 'TicketAssigned',
    message: 'admin assigned "Fix login" to you',
    actorId: 'admin',
    occurredAt: '2026-07-25T10:00:00Z',
    ...overrides,
  });
}

function boardChange(overrides: Partial<BoardChange>): string {
  return JSON.stringify({
    boardId: 1,
    type: 'TicketMoved',
    actorId: 'admin',
    occurredAt: '2026-07-25T10:00:00Z',
    ...overrides,
  });
}

class FakeEventSource {
  static readonly CONNECTING = 0;
  static readonly CLOSED = 2;

  readyState = 1;
  closed = false;

  private readonly listeners = new Map<string, ((event: MessageEvent) => void)[]>();

  constructor(readonly url: string) {}

  addEventListener(type: string, listener: (event: MessageEvent) => void): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  close(): void {
    this.closed = true;
  }

  emit(type: string, data: string): void {
    for (const listener of this.listeners.get(type) ?? []) {
      listener({ data } as MessageEvent);
    }
  }
}
