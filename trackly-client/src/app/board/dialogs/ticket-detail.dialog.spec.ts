import { HttpErrorResponse } from '@angular/common/http';
import { ANIMATION_MODULE_TYPE, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable, Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { aComment, aTicket } from '../../../testing/board.fixtures';
import { click, query, type } from '../../../testing/dom';
import { ActivityStreamService } from '../../core/activity-stream.service';
import { AuthService } from '../../core/auth.service';
import { BoardApiService } from '../../core/board-api.service';
import { BoardChange, Comment } from '../../core/board.models';
import { provideTracklyIcons } from '../../core/icons';
import { TICKET_COMMENTED, TicketDetailData, TicketDetailDialog } from './ticket-detail.dialog';

const close = vi.fn();

function aBoardChange(overrides: Partial<BoardChange> = {}): BoardChange {
  return {
    boardId: 1,
    type: TICKET_COMMENTED,
    actorId: 'admin',
    occurredAt: '2026-08-28T09:16:00Z',
    ...overrides,
  };
}

interface Harness {
  readonly fixture: ComponentFixture<TicketDetailDialog>;
  readonly api: {
    listComments: ReturnType<typeof vi.fn>;
    postComment: ReturnType<typeof vi.fn>;
    deleteComment: ReturnType<typeof vi.fn>;
  };
  readonly boardChanges: Subject<BoardChange>;
}

async function build(options: {
  thread?: Comment[];
  listComments?: () => Observable<Comment[]>;
  postComment?: () => Observable<Comment>;
  deleteComment?: () => Observable<void>;
  username?: string;
  admin?: boolean;
}): Promise<Harness> {
  close.mockClear();

  const api = {
    listComments: vi.fn(options.listComments ?? (() => of(options.thread ?? []))),
    postComment: vi.fn(options.postComment ?? (() => of(aComment({ id: 501, body: 'Posted' })))),
    deleteComment: vi.fn(options.deleteComment ?? (() => of(undefined))),
  };
  const boardChanges = new Subject<BoardChange>();
  const data: TicketDetailData = { ticket: aTicket() };

  await TestBed.configureTestingModule({
    imports: [TicketDetailDialog],
    providers: [
      provideZonelessChangeDetection(),
      { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
      provideTracklyIcons(),
      { provide: MAT_DIALOG_DATA, useValue: data },
      { provide: MatDialogRef, useValue: { close } },
      { provide: BoardApiService, useValue: api },
      {
        provide: AuthService,
        useValue: {
          username: signal(options.username ?? 'demo'),
          isAdmin: signal(options.admin ?? false),
        },
      },
      { provide: ActivityStreamService, useValue: { boardChanges: boardChanges.asObservable() } },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(TicketDetailDialog);
  await fixture.whenStable();
  return { fixture, api, boardChanges };
}

describe('TicketDetailDialog', () => {
  it('asks board-service for the thread as soon as it opens', async () => {
    const { fixture, api } = await build({ thread: [aComment()] });

    expect(api.listComments).toHaveBeenCalledWith(100);
    expect(query(fixture, 'comment-body-500')?.textContent?.trim()).toBe(
      'Blocked on the gateway route',
    );
    expect(query(fixture, 'comment-author-500')?.textContent?.trim()).toBe('demo');
  });

  it('says so when a ticket has never been commented on', async () => {
    const { fixture } = await build({ thread: [] });

    expect(query(fixture, 'comments-empty')).not.toBeNull();
  });

  it('appends what the user posted without re-reading the whole thread', async () => {
    const { fixture, api } = await build({
      thread: [aComment()],
      postComment: () => of(aComment({ id: 501, authorId: 'demo', body: 'One more thing' })),
    });

    type(fixture, 'comment-input', 'One more thing');
    await fixture.whenStable();
    click(fixture, 'comment-submit');
    await fixture.whenStable();

    expect(api.postComment).toHaveBeenCalledWith(100, 'One more thing');
    expect(api.listComments).toHaveBeenCalledTimes(1);
    expect(query(fixture, 'comment-body-501')?.textContent?.trim()).toBe('One more thing');
  });

  it('trims what was typed, since board-service rejects a blank body', async () => {
    const { fixture, api } = await build({});

    type(fixture, 'comment-input', '   Trimmed   ');
    await fixture.whenStable();
    click(fixture, 'comment-submit');
    await fixture.whenStable();

    expect(api.postComment).toHaveBeenCalledWith(100, 'Trimmed');
  });

  it('refuses a comment that is only whitespace without asking the server', async () => {
    const { fixture, api } = await build({});

    type(fixture, 'comment-input', '   ');
    await fixture.whenStable();
    click(fixture, 'comment-submit');
    await fixture.whenStable();

    expect(api.postComment).not.toHaveBeenCalled();
  });

  it('reports the problem detail the service gave when a post is refused', async () => {
    const { fixture } = await build({
      postComment: () =>
        throwError(
          () => new HttpErrorResponse({ status: 404, error: { detail: 'Ticket 100 not found' } }),
        ),
    });

    type(fixture, 'comment-input', 'Into the void');
    await fixture.whenStable();
    click(fixture, 'comment-submit');
    await fixture.whenStable();

    expect(query(fixture, 'comment-error')?.textContent?.trim()).toBe('Ticket 100 not found');
  });

  it('reports the problem detail when the thread cannot be loaded', async () => {
    const { fixture } = await build({
      listComments: () => throwError(() => new HttpErrorResponse({ status: 0 })),
    });

    expect(query(fixture, 'comment-error')?.textContent?.trim()).toBe(
      'Trackly is unreachable. Check your connection and try again.',
    );
  });

  it('lets a user delete their own comment and drops it from the thread', async () => {
    const { fixture, api } = await build({
      thread: [aComment({ id: 500, authorId: 'demo' })],
      username: 'demo',
    });

    click(fixture, 'delete-comment-500');
    await fixture.whenStable();

    expect(api.deleteComment).toHaveBeenCalledWith(100, 500);
    expect(query(fixture, 'comment-500')).toBeNull();
  });

  it("offers no delete on somebody else's comment", async () => {
    const { fixture } = await build({
      thread: [aComment({ id: 500, authorId: 'admin' })],
      username: 'demo',
      admin: false,
    });

    expect(query(fixture, 'delete-comment-500')).toBeNull();
  });

  it("lets an admin delete somebody else's comment", async () => {
    const { fixture } = await build({
      thread: [aComment({ id: 500, authorId: 'demo' })],
      username: 'admin',
      admin: true,
    });

    expect(query(fixture, 'delete-comment-500')).not.toBeNull();
  });

  it('reports the problem detail when a delete is refused', async () => {
    const { fixture } = await build({
      thread: [aComment({ id: 500, authorId: 'demo' })],
      username: 'demo',
      deleteComment: () => throwError(() => new HttpErrorResponse({ status: 403 })),
    });

    click(fixture, 'delete-comment-500');
    await fixture.whenStable();

    expect(query(fixture, 'comment-error')?.textContent?.trim()).toBe(
      'You do not have permission to do that.',
    );
    expect(query(fixture, 'comment-500')).not.toBeNull();
  });

  it('re-reads the thread when somebody else comments on this ticket', async () => {
    const { fixture, api, boardChanges } = await build({ thread: [] });

    boardChanges.next(aBoardChange({ actorId: 'admin' }));
    await new Promise((resolve) => setTimeout(resolve, 300));
    await fixture.whenStable();

    expect(api.listComments).toHaveBeenCalledTimes(2);
  });

  it('ignores the change its own comment caused, since the thread already shows it', async () => {
    const { fixture, api, boardChanges } = await build({ thread: [], username: 'demo' });

    boardChanges.next(aBoardChange({ actorId: 'demo' }));
    await new Promise((resolve) => setTimeout(resolve, 300));
    await fixture.whenStable();

    expect(api.listComments).toHaveBeenCalledTimes(1);
  });

  it('ignores board changes that are not comments, which the board itself answers', async () => {
    const { fixture, api, boardChanges } = await build({ thread: [] });

    boardChanges.next(aBoardChange({ type: 'TicketMoved', actorId: 'admin' }));
    await new Promise((resolve) => setTimeout(resolve, 300));
    await fixture.whenStable();

    expect(api.listComments).toHaveBeenCalledTimes(1);
  });

  it('closes when asked', async () => {
    const { fixture } = await build({});

    click(fixture, 'ticket-detail-close');

    expect(close).toHaveBeenCalled();
  });
});
