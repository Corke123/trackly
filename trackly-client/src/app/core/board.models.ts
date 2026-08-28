/**
 * The board vocabulary, mirroring board-service's `BoardView` and the glossary in CONTEXT.md:
 * a Board owns ordered Swimlanes, each holding Tickets.
 */

export interface Ticket {
  readonly id: number;
  readonly title: string;
  readonly description: string | null;
  readonly assigneeId: string | null;
  readonly position: number;
}

/** A remark a user left on a Ticket. Never edited; a correction is another Comment. */
export interface Comment {
  readonly id: number;
  readonly ticketId: number;
  readonly authorId: string;
  readonly body: string;
  readonly createdAt: string;
}

export interface Swimlane {
  readonly id: number;
  readonly title: string;
  readonly tickets: readonly Ticket[];
}

export interface Board {
  readonly id: number;
  readonly name: string;
  readonly swimlanes: readonly Swimlane[];
}

export interface BoardSummary {
  readonly id: number;
  readonly name: string;
}

/** A user that a ticket can be assigned to, as listed by identity-service. */
export interface User {
  readonly username: string;
}

export interface ActivityNotification {
  readonly id: number;
  readonly boardId: number;
  readonly type: string;
  readonly message: string;
  readonly actorId: string;
  readonly occurredAt: string;
}

export interface BoardChange {
  readonly boardId: number;
  readonly type: string;
  readonly actorId: string;
  readonly occurredAt: string;
}

/** Who the gateway says is signed in, and what they are allowed to do. */
export interface CurrentUser {
  readonly username: string;
  readonly roles: readonly string[];
  readonly admin: boolean;
}
