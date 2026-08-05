import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { BoardApiService } from './board-api.service';
import { Board, Swimlane, Ticket, User } from './board.models';
import { NotificationService } from './notification.service';
import { describeError } from './problem-detail';

/**
 * Holds the one board the app is about (CONTEXT.md: Trackly is one board per deployment) and every
 * change made to it.
 *
 * Moves are applied to the local board first and only then sent: a drag that waits for a round trip
 * before it settles reads as a broken drag. If the server refuses, the board is reloaded, so what is
 * on screen always ends up being what was persisted.
 */
@Injectable({ providedIn: 'root' })
export class BoardStore {
  private readonly api = inject(BoardApiService);
  private readonly notifications = inject(NotificationService);

  private readonly boardState = signal<Board | null>(null);
  private readonly usersState = signal<readonly User[]>([]);
  private readonly loadingState = signal(false);
  private readonly loadErrorState = signal<string | null>(null);

  readonly board = this.boardState.asReadonly();
  readonly users = this.usersState.asReadonly();
  readonly loading = this.loadingState.asReadonly();
  readonly loadError = this.loadErrorState.asReadonly();

  readonly boardName = computed(() => this.boardState()?.name ?? '');
  readonly swimlanes = computed<readonly Swimlane[]>(() => this.boardState()?.swimlanes ?? []);
  /**
   * Compared element-wise: this feeds every lane's `cdkDropListConnectedTo`, and the set of lanes
   * does not change when a ticket moves — only when a swimlane is added, removed or reordered.
   */
  readonly swimlaneListIds = computed(
    () => this.swimlanes().map((lane) => swimlaneListId(lane.id)),
    { equal: (a, b) => a.length === b.length && a.every((id, index) => id === b[index]) },
  );
  readonly hasBoard = computed(() => this.boardState() !== null);

  /** Discovers the single board and its assignable users. */
  async load(): Promise<void> {
    this.loadingState.set(true);
    this.loadErrorState.set(null);
    try {
      const summaries = await firstValueFrom(this.api.listBoards());
      const first = summaries[0];
      if (!first) {
        this.boardState.set(null);
        this.loadErrorState.set('No board has been created yet.');
        return;
      }
      // The board and the user directory are independent, and both are needed before the board is
      // usable — fetching them together saves a round trip on every page load.
      const [board] = await Promise.all([
        firstValueFrom(this.api.getBoard(first.id)),
        this.loadUsers(),
      ]);
      this.boardState.set(board);
    } catch (error) {
      this.loadErrorState.set(describeError(error, 'The board could not be loaded.'));
    } finally {
      this.loadingState.set(false);
    }
  }

  async renameBoard(name: string): Promise<void> {
    const board = this.requireBoard();
    try {
      this.boardState.set(await firstValueFrom(this.api.renameBoard(board.id, name)));
      this.notifications.notify(`Board renamed to "${name}".`);
    } catch (error) {
      this.notifications.reportError(describeError(error, 'The board could not be renamed.'));
    }
  }

  async addSwimlane(title: string): Promise<void> {
    const board = this.requireBoard();
    try {
      const created = await firstValueFrom(this.api.addSwimlane(board.id, title));
      this.boardState.update((current) =>
        current === null
          ? current
          : { ...current, swimlanes: [...current.swimlanes, { ...created, tickets: [] }] },
      );
      this.notifications.notify(`Swimlane "${title}" added.`);
    } catch (error) {
      this.notifications.reportError(describeError(error, 'The swimlane could not be added.'));
    }
  }

  async deleteSwimlane(swimlaneId: number): Promise<void> {
    const board = this.requireBoard();
    try {
      await firstValueFrom(this.api.deleteSwimlane(board.id, swimlaneId));
      this.boardState.update((current) =>
        current === null
          ? current
          : { ...current, swimlanes: current.swimlanes.filter((lane) => lane.id !== swimlaneId) },
      );
      this.notifications.notify('Swimlane deleted.');
    } catch (error) {
      this.notifications.reportError(describeError(error, 'The swimlane could not be deleted.'));
    }
  }

  async moveSwimlane(fromIndex: number, toIndex: number): Promise<void> {
    const board = this.requireBoard();
    const reordered = moveItem(board.swimlanes, fromIndex, toIndex);
    if (reordered === board.swimlanes) {
      return;
    }

    this.boardState.set({ ...board, swimlanes: reordered });
    try {
      await firstValueFrom(
        this.api.reorderSwimlanes(
          board.id,
          reordered.map((lane) => lane.id),
        ),
      );
    } catch (error) {
      this.notifications.reportError(describeError(error, 'The swimlanes could not be reordered.'));
      await this.reload();
    }
  }

  async createTicket(swimlaneId: number, title: string, description: string | null): Promise<void> {
    const board = this.requireBoard();
    try {
      const created = await firstValueFrom(
        this.api.createTicket(board.id, swimlaneId, title, description),
      );
      this.boardState.update((current) =>
        current === null
          ? current
          : {
              ...current,
              swimlanes: current.swimlanes.map((lane) =>
                lane.id === swimlaneId ? { ...lane, tickets: [...lane.tickets, created] } : lane,
              ),
            },
      );
      this.notifications.notify(`Ticket "${title}" created.`);
    } catch (error) {
      this.notifications.reportError(describeError(error, 'The ticket could not be created.'));
    }
  }

  async moveTicket(
    ticketId: number,
    fromSwimlaneId: number,
    toSwimlaneId: number,
    toIndex: number,
  ): Promise<void> {
    const board = this.requireBoard();
    const moved = moveTicketWithin(board, ticketId, fromSwimlaneId, toSwimlaneId, toIndex);
    if (moved === null) {
      return;
    }

    this.boardState.set(moved);
    try {
      await firstValueFrom(this.api.moveTicket(ticketId, toSwimlaneId, toIndex));
    } catch (error) {
      this.notifications.reportError(describeError(error, 'The ticket could not be moved.'));
      await this.reload();
    }
  }

  async assignTicket(ticketId: number, assigneeId: string): Promise<void> {
    try {
      const assigned = await firstValueFrom(this.api.assignTicket(ticketId, assigneeId));
      // Only the lane holding the ticket is rebuilt: handing every other lane a new object would
      // make every column and card re-render for a change to one field of one ticket.
      this.boardState.update((current) =>
        current === null
          ? current
          : {
              ...current,
              swimlanes: current.swimlanes.map((lane) =>
                lane.tickets.some((ticket) => ticket.id === ticketId)
                  ? {
                      ...lane,
                      tickets: lane.tickets.map((ticket) =>
                        ticket.id === ticketId ? { ...ticket, ...assigned } : ticket,
                      ),
                    }
                  : lane,
              ),
            },
      );
      this.notifications.notify(`Ticket assigned to ${assigneeId}.`);
    } catch (error) {
      this.notifications.reportError(describeError(error, 'The ticket could not be assigned.'));
    }
  }

  private async loadUsers(): Promise<void> {
    try {
      this.usersState.set(await firstValueFrom(this.api.listUsers()));
    } catch {
      // Assignment is still possible by typing a username; an empty list is not worth an error.
      this.usersState.set([]);
    }
  }

  private async reload(): Promise<void> {
    const board = this.boardState();
    if (board === null) {
      return;
    }
    try {
      this.boardState.set(await firstValueFrom(this.api.getBoard(board.id)));
    } catch (error) {
      this.loadErrorState.set(describeError(error, 'The board could not be reloaded.'));
    }
  }

  private requireBoard(): Board {
    const board = this.boardState();
    if (board === null) {
      throw new Error('No board is loaded.');
    }
    return board;
  }
}

/** The CDK drop list id for a swimlane's tickets, shared by the lane and everything connecting to it. */
export function swimlaneListId(swimlaneId: number): string {
  return `swimlane-${swimlaneId}`;
}

function moveItem<T>(items: readonly T[], fromIndex: number, toIndex: number): readonly T[] {
  const bounded = Math.max(0, Math.min(toIndex, items.length - 1));
  if (fromIndex === bounded || fromIndex < 0 || fromIndex >= items.length) {
    return items;
  }

  const next = [...items];
  const [moved] = next.splice(fromIndex, 1);
  next.splice(bounded, 0, moved);
  return next;
}

function moveTicketWithin(
  board: Board,
  ticketId: number,
  fromSwimlaneId: number,
  toSwimlaneId: number,
  toIndex: number,
): Board | null {
  const source = board.swimlanes.find((lane) => lane.id === fromSwimlaneId);
  const ticket = source?.tickets.find((candidate) => candidate.id === ticketId);
  if (!source || !ticket || !board.swimlanes.some((lane) => lane.id === toSwimlaneId)) {
    return null;
  }

  // Lanes the move did not touch are handed back as they were: a new object for them would
  // re-render every column and card on the board for a drag that concerns at most two lanes.
  const swimlanes = board.swimlanes.map((lane) => {
    if (lane.id === fromSwimlaneId && lane.id === toSwimlaneId) {
      const remaining = lane.tickets.filter((candidate) => candidate.id !== ticketId);
      return renumber(lane, insertAt(remaining, ticket, toIndex));
    }
    if (lane.id === fromSwimlaneId) {
      return renumber(
        lane,
        lane.tickets.filter((candidate) => candidate.id !== ticketId),
      );
    }
    if (lane.id === toSwimlaneId) {
      return renumber(lane, insertAt(lane.tickets, ticket, toIndex));
    }
    return lane;
  });

  return { ...board, swimlanes };
}

function insertAt(tickets: readonly Ticket[], ticket: Ticket, index: number): Ticket[] {
  const next = [...tickets];
  next.splice(Math.max(0, Math.min(index, next.length)), 0, ticket);
  return next;
}

/** Keeps the local positions matching what board-service writes, so a later reload shows no jump. */
function renumber(lane: Swimlane, tickets: readonly Ticket[]): Swimlane {
  return {
    ...lane,
    tickets: tickets.map((ticket, index) => ({ ...ticket, position: index })),
  };
}
