import { DestroyRef, Injectable, InjectionToken, inject } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { ActivityNotification } from './board.models';

export const ACTIVITY_EVENT = 'activity';

export const RECONNECT_MIN_DELAY = 1_000;
export const RECONNECT_MAX_DELAY = 30_000;

const CLOSED = 2;

export const EVENT_SOURCE_FACTORY = new InjectionToken<(url: string) => EventSource>(
  'EVENT_SOURCE_FACTORY',
  { providedIn: 'root', factory: () => (url: string) => new EventSource(url) },
);

@Injectable({ providedIn: 'root' })
export class ActivityStreamService {
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly newEventSource = inject(EVENT_SOURCE_FACTORY);

  private source: EventSource | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private retryDelay = RECONNECT_MIN_DELAY;
  private lastEventId: string | null = null;

  private readonly received = new Subject<ActivityNotification>();

  readonly notifications: Observable<ActivityNotification> = this.received.asObservable();

  constructor() {
    inject(DestroyRef).onDestroy(() => this.disconnect());
  }

  connect(): void {
    if (this.source) {
      return;
    }

    const source = this.newEventSource(this.streamUrl());
    this.source = source;

    source.addEventListener(ACTIVITY_EVENT, (event) => this.onActivity(event as MessageEvent));
    source.addEventListener('open', () => (this.retryDelay = RECONNECT_MIN_DELAY));
    source.addEventListener('error', () => this.onError());
  }

  disconnect(): void {
    this.source?.close();
    this.source = null;
    this.clearRetry();
    this.retryDelay = RECONNECT_MIN_DELAY;
  }

  private streamUrl(): string {
    const stream = `${this.baseUrl}/activity/stream`;
    return this.lastEventId === null
      ? stream
      : `${stream}?lastEventId=${encodeURIComponent(this.lastEventId)}`;
  }

  private onActivity(event: MessageEvent): void {
    const notification = parseNotification(event.data);
    if (notification) {
      this.lastEventId = event.lastEventId || String(notification.id);
      this.received.next(notification);
    }
  }

  private onError(): void {
    if (this.source?.readyState !== CLOSED || this.retryTimer !== null) {
      return;
    }

    this.source.close();
    this.source = null;

    const delay = this.retryDelay;
    this.retryDelay = Math.min(delay * 2, RECONNECT_MAX_DELAY);
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      this.connect();
    }, delay);
  }

  private clearRetry(): void {
    if (this.retryTimer !== null) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }
}

function parseNotification(data: unknown): ActivityNotification | null {
  if (typeof data !== 'string') {
    return null;
  }

  try {
    const parsed: unknown = JSON.parse(data);
    return isNotification(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function isNotification(value: unknown): value is ActivityNotification {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ActivityNotification).id === 'number' &&
    typeof (value as ActivityNotification).message === 'string'
  );
}
