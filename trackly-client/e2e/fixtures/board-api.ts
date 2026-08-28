import { Page, Route, test as base } from '@playwright/test';

interface TicketState {
  id: number;
  title: string;
  description: string | null;
  assigneeId: string | null;
  swimlaneId: number;
}

interface CommentState {
  id: number;
  ticketId: number;
  authorId: string;
  body: string;
  createdAt: string;
}

interface SwimlaneState {
  id: number;
  title: string;
}

/**
 * A stand-in for the gateway that keeps board-service's rules: only an admin may shape the board, a
 * swimlane holding tickets cannot be deleted, and positions stay dense. Those rules are what the
 * journeys are about, so the stub has to honor them rather than say yes to everything.
 */
export class FakeBoard {
  private nextId = 1000;

  name = 'Trackly Board';

  swimlanes: SwimlaneState[] = [
    { id: 10, title: 'To Do' },
    { id: 20, title: 'In Progress' },
    { id: 30, title: 'Done' },
  ];

  tickets: TicketState[] = [
    {
      id: 100,
      title: 'Wire up the pipeline',
      description: 'Blue-green on Container Apps',
      assigneeId: null,
      swimlaneId: 10,
    },
    {
      id: 200,
      title: 'Write the ADR',
      description: null,
      assigneeId: 'demo',
      swimlaneId: 20,
    },
  ];

  comments: CommentState[] = [];

  constructor(readonly admin: boolean) {}

  get actor(): string {
    return this.admin ? 'admin' : 'demo';
  }

  view() {
    return {
      id: 1,
      name: this.name,
      swimlanes: this.swimlanes.map((lane) => ({
        id: lane.id,
        title: lane.title,
        tickets: this.tickets
          .filter((ticket) => ticket.swimlaneId === lane.id)
          .map((ticket, position) => ({ ...ticket, position })),
      })),
    };
  }

  addSwimlane(title: string): SwimlaneState {
    const lane = { id: this.nextId++, title };
    this.swimlanes.push(lane);
    return lane;
  }

  deleteSwimlane(swimlaneId: number): { conflict: boolean } {
    const held = this.tickets.filter((ticket) => ticket.swimlaneId === swimlaneId).length;
    if (held > 0) {
      return { conflict: true };
    }
    this.swimlanes = this.swimlanes.filter((lane) => lane.id !== swimlaneId);
    return { conflict: false };
  }

  reorderSwimlanes(order: number[]): void {
    this.swimlanes = order.map(
      (id) => this.swimlanes.find((lane) => lane.id === id) as SwimlaneState,
    );
  }

  createTicket(swimlaneId: number, title: string, description: string | null): TicketState {
    const ticket = { id: this.nextId++, title, description, assigneeId: null, swimlaneId };
    this.tickets.push(ticket);
    return ticket;
  }

  moveTicket(ticketId: number, swimlaneId: number, position: number): TicketState | null {
    const ticket = this.tickets.find((candidate) => candidate.id === ticketId);
    if (!ticket) {
      return null;
    }

    this.tickets = this.tickets.filter((candidate) => candidate.id !== ticketId);
    ticket.swimlaneId = swimlaneId;

    const inLane = this.tickets.filter((candidate) => candidate.swimlaneId === swimlaneId);
    const others = this.tickets.filter((candidate) => candidate.swimlaneId !== swimlaneId);
    inLane.splice(Math.min(position, inLane.length), 0, ticket);
    this.tickets = [...others, ...inLane];

    return ticket;
  }

  assignTicket(ticketId: number, assigneeId: string): TicketState | null {
    const ticket = this.tickets.find((candidate) => candidate.id === ticketId);
    if (!ticket) {
      return null;
    }
    ticket.assigneeId = assigneeId;
    return ticket;
  }

  deleteTicket(ticketId: number): boolean {
    const held = this.tickets.some((candidate) => candidate.id === ticketId);
    this.tickets = this.tickets.filter((candidate) => candidate.id !== ticketId);
    return held;
  }

  commentsOn(ticketId: number): CommentState[] {
    return this.comments.filter((comment) => comment.ticketId === ticketId);
  }

  postComment(ticketId: number, authorId: string, body: string): CommentState | null {
    if (!this.tickets.some((ticket) => ticket.id === ticketId)) {
      return null;
    }
    const comment = {
      id: this.nextId++,
      ticketId,
      authorId,
      body,
      createdAt: new Date().toISOString(),
    };
    this.comments.push(comment);
    return comment;
  }

  deleteComment(
    ticketId: number,
    commentId: number,
    actorId: string,
  ): 'deleted' | 'forbidden' | 'missing' {
    const comment = this.comments.find(
      (candidate) => candidate.id === commentId && candidate.ticketId === ticketId,
    );
    if (!comment) {
      return 'missing';
    }
    if (!this.admin && comment.authorId !== actorId) {
      return 'forbidden';
    }
    this.comments = this.comments.filter((candidate) => candidate.id !== commentId);
    return 'deleted';
  }

  /** Where the tickets in a swimlane sit, in order — what a drag is judged by. */
  titlesIn(swimlaneId: number): string[] {
    return this.tickets
      .filter((ticket) => ticket.swimlaneId === swimlaneId)
      .map((ticket) => ticket.title);
  }
}

export class FakeActivityStream {
  private queued: string[] = [];
  private nextId = 1;

  push(message: string, type = 'TicketAssigned'): void {
    const id = this.nextId++;
    const notification = {
      id,
      boardId: 1,
      type,
      message,
      actorId: 'admin',
      occurredAt: new Date().toISOString(),
    };
    this.queued.push(`id: ${id}\nevent: activity\ndata: ${JSON.stringify(notification)}\n\n`);
  }

  pushBoardChange(type = 'TicketMoved', actorId = 'admin'): void {
    const change = { boardId: 1, type, actorId, occurredAt: new Date().toISOString() };
    this.queued.push(`event: board-changed\ndata: ${JSON.stringify(change)}\n\n`);
  }

  drain(): string {
    const body = ['retry: 300\n\n', ...this.queued].join('');
    this.queued = [];
    return body;
  }
}

export async function installBoardApi(
  page: Page,
  board: FakeBoard,
  activity: FakeActivityStream,
): Promise<void> {
  const json = (route: Route, body: unknown, status = 200) =>
    route.fulfill({ status, contentType: 'application/json', json: body as object });

  const forbidden = (route: Route) => json(route, { status: 403, detail: 'Access Denied' }, 403);

  // Logging out is a navigation in the real app; here it just has to land somewhere recognisable.
  await page.route('**/logout', (route) =>
    route.fulfill({ status: 200, contentType: 'text/html', body: '<h1>Signed out</h1>' }),
  );

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    const body = request.postData() ? JSON.parse(request.postData() as string) : {};

    if (path === '/api/activity/stream') {
      return route.fulfill({
        status: 200,
        headers: { 'content-type': 'text/event-stream', 'cache-control': 'no-cache' },
        body: activity.drain(),
      });
    }

    if (path === '/api/me') {
      return json(route, {
        username: board.admin ? 'admin' : 'demo',
        roles: [board.admin ? 'ROLE_ADMIN' : 'ROLE_USER'],
        admin: board.admin,
      });
    }

    if (path === '/api/users') {
      return json(route, [{ username: 'admin' }, { username: 'demo' }, { username: 'user' }]);
    }

    if (path === '/api/boards' && method === 'GET') {
      return json(route, [{ id: 1, name: board.name }]);
    }

    if (path === '/api/boards/1' && method === 'GET') {
      return json(route, board.view());
    }

    if (path === '/api/boards/1' && method === 'PATCH') {
      if (!board.admin) {
        return forbidden(route);
      }
      board.name = body.name;
      return json(route, board.view());
    }

    if (path === '/api/boards/1/swimlanes' && method === 'POST') {
      if (!board.admin) {
        return forbidden(route);
      }
      const lane = board.addSwimlane(body.title);
      return json(route, { ...lane, tickets: [] }, 201);
    }

    if (path === '/api/boards/1/swimlanes/order' && method === 'PUT') {
      if (!board.admin) {
        return forbidden(route);
      }
      board.reorderSwimlanes(body.swimlaneIds);
      return json(route, board.view());
    }

    const deleteMatch = /^\/api\/boards\/1\/swimlanes\/(\d+)$/.exec(path);
    if (deleteMatch && method === 'DELETE') {
      if (!board.admin) {
        return forbidden(route);
      }
      const swimlaneId = Number(deleteMatch[1]);
      const { conflict } = board.deleteSwimlane(swimlaneId);
      if (conflict) {
        return json(
          route,
          {
            status: 409,
            detail: `Swimlane ${swimlaneId} still holds 1 ticket(s) and cannot be deleted`,
          },
          409,
        );
      }
      return route.fulfill({ status: 204, body: '' });
    }

    if (path === '/api/boards/1/tickets' && method === 'POST') {
      const ticket = board.createTicket(body.swimlaneId, body.title, body.description ?? null);
      return json(route, { ...ticket, position: 0 }, 201);
    }

    const ticketMatch = /^\/api\/tickets\/(\d+)$/.exec(path);
    if (ticketMatch && method === 'DELETE') {
      if (!board.admin) {
        return forbidden(route);
      }
      const ticketId = Number(ticketMatch[1]);
      if (!board.deleteTicket(ticketId)) {
        return json(route, { status: 404, detail: `Ticket ${ticketId} not found` }, 404);
      }
      return route.fulfill({ status: 204, body: '' });
    }

    if (ticketMatch && method === 'PATCH') {
      const ticketId = Number(ticketMatch[1]);
      const updated =
        body.assigneeId !== undefined
          ? board.assignTicket(ticketId, body.assigneeId)
          : board.moveTicket(ticketId, body.swimlaneId, body.position);

      if (!updated) {
        return json(route, { status: 404, detail: `Ticket ${ticketId} not found` }, 404);
      }
      return json(route, { ...updated, position: body.position ?? 0 });
    }

    const threadMatch = /^\/api\/tickets\/(\d+)\/comments$/.exec(path);
    if (threadMatch && method === 'GET') {
      return json(route, board.commentsOn(Number(threadMatch[1])));
    }

    if (threadMatch && method === 'POST') {
      const ticketId = Number(threadMatch[1]);
      const comment = board.postComment(ticketId, board.actor, body.body);
      if (!comment) {
        return json(route, { status: 404, detail: `Ticket ${ticketId} not found` }, 404);
      }
      return json(route, comment, 201);
    }

    const commentMatch = /^\/api\/tickets\/(\d+)\/comments\/(\d+)$/.exec(path);
    if (commentMatch && method === 'DELETE') {
      const commentId = Number(commentMatch[2]);
      const outcome = board.deleteComment(Number(commentMatch[1]), commentId, board.actor);
      if (outcome === 'missing') {
        return json(route, { status: 404, detail: `Comment ${commentId} not found` }, 404);
      }
      if (outcome === 'forbidden') {
        return json(
          route,
          { status: 403, detail: `Comment ${commentId} was written by somebody else` },
          403,
        );
      }
      return route.fulfill({ status: 204, body: '' });
    }

    return json(route, { status: 404, detail: `No stub for ${method} ${path}` }, 404);
  });
}

interface TracklyFixtures {
  /** Signed in as `admin`, who may shape the board. */
  adminBoard: FakeBoard;
  /** Signed in as `demo`, who may only work with tickets. */
  userBoard: FakeBoard;
  activityStream: FakeActivityStream;
}

export const test = base.extend<TracklyFixtures>({
  activityStream: async ({}, use) => {
    await use(new FakeActivityStream());
  },
  adminBoard: async ({ page, activityStream }, use) => {
    const board = new FakeBoard(true);
    await installBoardApi(page, board, activityStream);
    await use(board);
  },
  userBoard: async ({ page, activityStream }, use) => {
    const board = new FakeBoard(false);
    await installBoardApi(page, board, activityStream);
    await use(board);
  },
});

export { expect } from '@playwright/test';
