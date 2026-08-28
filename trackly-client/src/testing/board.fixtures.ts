import { Board, Comment, CurrentUser, Swimlane, Ticket, User } from '../app/core/board.models';

export function aTicket(overrides: Partial<Ticket> = {}): Ticket {
  return {
    id: 100,
    title: 'Write tests',
    description: 'Cover the happy paths',
    assigneeId: null,
    position: 0,
    ...overrides,
  };
}

export function aComment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: 500,
    ticketId: 100,
    authorId: 'demo',
    body: 'Blocked on the gateway route',
    createdAt: '2026-08-28T09:15:00Z',
    ...overrides,
  };
}

export function aSwimlane(overrides: Partial<Swimlane> = {}): Swimlane {
  return { id: 10, title: 'To Do', tickets: [], ...overrides };
}

/** Three lanes, one ticket on each of the first two — the shape most behavior needs to be shown on. */
export function aBoard(overrides: Partial<Board> = {}): Board {
  return {
    id: 1,
    name: 'Trackly Board',
    swimlanes: [
      aSwimlane({ id: 10, title: 'To Do', tickets: [aTicket({ id: 100, title: 'First' })] }),
      aSwimlane({
        id: 20,
        title: 'In Progress',
        tickets: [aTicket({ id: 200, title: 'Second', assigneeId: 'demo' })],
      }),
      aSwimlane({ id: 30, title: 'Done', tickets: [] }),
    ],
    ...overrides,
  };
}

export function anAdmin(overrides: Partial<CurrentUser> = {}): CurrentUser {
  return { username: 'admin', roles: ['ROLE_ADMIN'], admin: true, ...overrides };
}

export function aPlainUser(overrides: Partial<CurrentUser> = {}): CurrentUser {
  return { username: 'demo', roles: ['ROLE_USER'], admin: false, ...overrides };
}

export function someUsers(): User[] {
  return [{ username: 'admin' }, { username: 'demo' }, { username: 'user' }];
}
