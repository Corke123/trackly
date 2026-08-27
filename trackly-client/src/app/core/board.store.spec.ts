import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject, of, throwError } from 'rxjs';
import { aBoard, aSwimlane, aTicket, someUsers } from '../../testing/board.fixtures';
import { BoardApiService } from './board-api.service';
import { Ticket } from './board.models';
import { BoardStore } from './board.store';
import { NotificationService } from './notification.service';

describe('BoardStore', () => {
  let api: {
    listBoards: ReturnType<typeof vi.fn>;
    getBoard: ReturnType<typeof vi.fn>;
    renameBoard: ReturnType<typeof vi.fn>;
    addSwimlane: ReturnType<typeof vi.fn>;
    deleteSwimlane: ReturnType<typeof vi.fn>;
    reorderSwimlanes: ReturnType<typeof vi.fn>;
    createTicket: ReturnType<typeof vi.fn>;
    moveTicket: ReturnType<typeof vi.fn>;
    assignTicket: ReturnType<typeof vi.fn>;
    deleteTicket: ReturnType<typeof vi.fn>;
    listUsers: ReturnType<typeof vi.fn>;
  };
  let notifications: { notify: ReturnType<typeof vi.fn>; reportError: ReturnType<typeof vi.fn> };
  let store: BoardStore;

  beforeEach(() => {
    api = {
      listBoards: vi.fn().mockReturnValue(of([{ id: 1, name: 'Trackly Board' }])),
      getBoard: vi.fn().mockReturnValue(of(aBoard())),
      renameBoard: vi.fn(),
      addSwimlane: vi.fn(),
      deleteSwimlane: vi.fn(),
      reorderSwimlanes: vi.fn().mockReturnValue(of(aBoard())),
      createTicket: vi.fn(),
      moveTicket: vi.fn().mockReturnValue(of(aTicket())),
      assignTicket: vi.fn(),
      deleteTicket: vi.fn(),
      listUsers: vi.fn().mockReturnValue(of(someUsers())),
    };
    notifications = { notify: vi.fn(), reportError: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        BoardStore,
        { provide: BoardApiService, useValue: api },
        { provide: NotificationService, useValue: notifications },
      ],
    });
    store = TestBed.inject(BoardStore);
  });

  describe('load', () => {
    it('opens the single board the deployment has, with its assignable users', async () => {
      await store.load();

      expect(store.boardName()).toBe('Trackly Board');
      expect(store.swimlanes().map((lane) => lane.title)).toEqual(['To Do', 'In Progress', 'Done']);
      expect(store.users()).toHaveLength(3);
      expect(store.loading()).toBe(false);
    });

    it('publishes drop list ids for every swimlane so the lanes can accept each other tickets', async () => {
      await store.load();

      expect(store.swimlaneListIds()).toEqual(['swimlane-10', 'swimlane-20', 'swimlane-30']);
    });

    it('reports that there is nothing to show when no board exists yet', async () => {
      api.listBoards.mockReturnValue(of([]));

      await store.load();

      expect(store.hasBoard()).toBe(false);
      expect(store.loadError()).toContain('No board');
    });

    it('surfaces the problem detail when the board cannot be fetched', async () => {
      api.getBoard.mockReturnValue(throwError(() => problem(500, 'Board 1 exploded')));

      await store.load();

      expect(store.loadError()).toBe('Board 1 exploded');
      expect(store.loading()).toBe(false);
    });

    it('still shows the board when the user directory is unavailable', async () => {
      api.listUsers.mockReturnValue(throwError(() => problem(503, 'identity is down')));

      await store.load();

      expect(store.hasBoard()).toBe(true);
      expect(store.users()).toEqual([]);
      expect(store.loadError()).toBeNull();
    });
  });

  describe('admin operations', () => {
    beforeEach(async () => {
      await store.load();
    });

    it('renames the board', async () => {
      api.renameBoard.mockReturnValue(of({ ...aBoard(), name: 'Release board' }));

      await store.renameBoard('Release board');

      expect(api.renameBoard).toHaveBeenCalledWith(1, 'Release board');
      expect(store.boardName()).toBe('Release board');
    });

    it('keeps the old name and explains why when renaming is refused', async () => {
      api.renameBoard.mockReturnValue(throwError(() => problem(403, 'Access Denied')));

      await store.renameBoard('Release board');

      expect(store.boardName()).toBe('Trackly Board');
      expect(notifications.reportError).toHaveBeenCalledWith('Access Denied');
    });

    it('appends a new swimlane', async () => {
      api.addSwimlane.mockReturnValue(of({ id: 40, title: 'Blocked', tickets: [] }));

      await store.addSwimlane('Blocked');

      expect(store.swimlanes().at(-1)).toEqual({ id: 40, title: 'Blocked', tickets: [] });
    });

    it('removes a deleted swimlane', async () => {
      api.deleteSwimlane.mockReturnValue(of(undefined));

      await store.deleteSwimlane(30);

      expect(store.swimlanes().map((lane) => lane.id)).toEqual([10, 20]);
    });

    it('keeps a swimlane that the board refuses to delete, and says why', async () => {
      api.deleteSwimlane.mockReturnValue(
        throwError(() => problem(409, 'Swimlane 10 still holds 1 ticket(s) and cannot be deleted')),
      );

      await store.deleteSwimlane(10);

      expect(store.swimlanes().map((lane) => lane.id)).toEqual([10, 20, 30]);
      expect(notifications.reportError).toHaveBeenCalledWith(
        'Swimlane 10 still holds 1 ticket(s) and cannot be deleted',
      );
    });

    it('reorders swimlanes and sends the whole new order', async () => {
      await store.moveSwimlane(2, 0);

      expect(store.swimlanes().map((lane) => lane.id)).toEqual([30, 10, 20]);
      expect(api.reorderSwimlanes).toHaveBeenCalledWith(1, [30, 10, 20]);
    });

    it('does nothing when a swimlane is dropped where it already was', async () => {
      await store.moveSwimlane(1, 1);

      expect(api.reorderSwimlanes).not.toHaveBeenCalled();
    });

    it('restores the order the server has when a reorder fails', async () => {
      api.reorderSwimlanes.mockReturnValue(throwError(() => problem(403, 'Access Denied')));

      await store.moveSwimlane(2, 0);

      expect(store.swimlanes().map((lane) => lane.id)).toEqual([10, 20, 30]);
      expect(notifications.reportError).toHaveBeenCalledWith('Access Denied');
    });
  });

  describe('tickets', () => {
    beforeEach(async () => {
      await store.load();
    });

    it('adds a created ticket to the swimlane it was created in', async () => {
      const created = aTicket({ id: 300, title: 'Third', position: 0 });
      api.createTicket.mockReturnValue(of(created));

      await store.createTicket(30, 'Third', 'Details');

      expect(api.createTicket).toHaveBeenCalledWith(1, 30, 'Third', 'Details');
      expect(laneById(store, 30).tickets).toEqual([created]);
    });

    it('moves a ticket between swimlanes before the server has answered', async () => {
      let resolved = false;
      api.moveTicket.mockReturnValue(of(aTicket()).pipe());

      const inFlight = store.moveTicket(100, 10, 30, 0).then(() => {
        resolved = true;
      });

      expect(laneById(store, 10).tickets).toEqual([]);
      expect(laneById(store, 30).tickets.map((ticket) => ticket.id)).toEqual([100]);
      expect(resolved).toBe(false);

      await inFlight;
      expect(api.moveTicket).toHaveBeenCalledWith(100, 30, 0);
    });

    it('renumbers positions so they match what the board persists', async () => {
      await store.moveTicket(100, 10, 20, 0);

      expect(laneById(store, 20).tickets.map((ticket) => [ticket.id, ticket.position])).toEqual([
        [100, 0],
        [200, 1],
      ]);
    });

    it('reorders a ticket within its own swimlane', async () => {
      api.createTicket.mockReturnValue(of(aTicket({ id: 101, title: 'Also here', position: 1 })));
      await store.createTicket(10, 'Also here', null);

      await store.moveTicket(101, 10, 10, 0);

      expect(laneById(store, 10).tickets.map((ticket) => ticket.id)).toEqual([101, 100]);
    });

    it('puts the board back the way the server has it when a move fails', async () => {
      api.moveTicket.mockReturnValue(
        throwError(() => problem(422, 'Swimlane 30 not on the board 1')),
      );

      await store.moveTicket(100, 10, 30, 0);

      expect(laneById(store, 10).tickets.map((ticket) => ticket.id)).toEqual([100]);
      expect(laneById(store, 30).tickets).toEqual([]);
      expect(notifications.reportError).toHaveBeenCalledWith('Swimlane 30 not on the board 1');
    });

    it('ignores a move of a ticket that is not where it was said to be', async () => {
      await store.moveTicket(999, 10, 30, 0);

      expect(api.moveTicket).not.toHaveBeenCalled();
    });

    it('assigns a ticket', async () => {
      api.assignTicket.mockReturnValue(
        of(aTicket({ id: 100, title: 'First', assigneeId: 'demo' })),
      );

      await store.assignTicket(100, 'demo');

      expect(api.assignTicket).toHaveBeenCalledWith(100, 'demo');
      expect(laneById(store, 10).tickets[0].assigneeId).toBe('demo');
    });

    it('removes a deleted ticket and closes the gap it leaves behind', async () => {
      api.deleteTicket.mockReturnValue(of(undefined));

      await store.deleteTicket(100);

      expect(api.deleteTicket).toHaveBeenCalledWith(100);
      expect(laneById(store, 10).tickets).toEqual([]);
    });

    it('renumbers the tickets left in the lane after a deletion', async () => {
      api.deleteTicket.mockReturnValue(of(undefined));
      const lane = aSwimlane({
        id: 10,
        title: 'To Do',
        tickets: [
          aTicket({ id: 100, title: 'First', position: 0 }),
          aTicket({ id: 101, title: 'Doomed', position: 1 }),
          aTicket({ id: 102, title: 'Third', position: 2 }),
        ],
      });
      api.getBoard.mockReturnValue(of(aBoard({ swimlanes: [lane] })));
      await store.load();

      await store.deleteTicket(101);

      expect(laneById(store, 10).tickets.map((ticket) => [ticket.id, ticket.position])).toEqual([
        [100, 0],
        [102, 1],
      ]);
    });

    it('keeps the ticket and says why when a deletion is refused', async () => {
      api.deleteTicket.mockReturnValue(throwError(() => problem(403, 'Access Denied')));

      await store.deleteTicket(100);

      expect(laneById(store, 10).tickets.map((ticket) => ticket.id)).toEqual([100]);
      expect(notifications.reportError).toHaveBeenCalledWith('Access Denied');
    });

    it('explains why an assignment did not take', async () => {
      api.assignTicket.mockReturnValue(throwError(() => problem(404, 'Ticket 100 not found')));

      await store.assignTicket(100, 'demo');

      expect(laneById(store, 10).tickets[0].assigneeId).toBeNull();
      expect(notifications.reportError).toHaveBeenCalledWith('Ticket 100 not found');
    });
  });
  describe('refresh', () => {
    beforeEach(async () => {
      await store.load();
      api.getBoard.mockClear();
    });

    it('picks up what another user changed', async () => {
      api.getBoard.mockReturnValue(
        of(aBoard({ swimlanes: [aSwimlane({ id: 10, title: 'To Do', tickets: [] })] })),
      );

      await store.refresh();

      expect(store.swimlanes().map((lane) => lane.title)).toEqual(['To Do']);
    });

    it('keeps the board on screen when a refresh cannot be fetched', async () => {
      api.getBoard.mockReturnValue(throwError(() => problem(503, 'Board service is asleep')));

      await store.refresh();

      expect(store.swimlanes().map((lane) => lane.title)).toEqual(['To Do', 'In Progress', 'Done']);
      expect(store.loadError()).toBeNull();
    });

    it('holds a refresh back until the write in flight has landed', async () => {
      const move = new Subject<Ticket>();
      api.moveTicket.mockReturnValue(move);
      const inFlight = store.moveTicket(100, 10, 30, 0);

      await store.refresh();
      expect(api.getBoard).not.toHaveBeenCalled();

      move.next(aTicket());
      move.complete();
      await inFlight;

      expect(api.getBoard).toHaveBeenCalledTimes(1);
    });

    it('asks for the board once when several writes were waiting on it', async () => {
      const first = new Subject<Ticket>();
      const second = new Subject<void>();
      api.moveTicket.mockReturnValue(first);
      api.deleteTicket.mockReturnValue(second);
      const moving = store.moveTicket(100, 10, 30, 0);
      const deleting = store.deleteTicket(200);

      await store.refresh();
      await store.refresh();

      first.next(aTicket());
      first.complete();
      await moving;
      expect(api.getBoard).not.toHaveBeenCalled();

      second.next();
      second.complete();
      await deleting;

      expect(api.getBoard).toHaveBeenCalledTimes(1);
    });
  });

  it('has nothing to refresh before a board has been opened', async () => {
    await store.refresh();

    expect(api.getBoard).not.toHaveBeenCalled();
  });
});

function laneById(store: BoardStore, id: number) {
  const lane = store.swimlanes().find((candidate) => candidate.id === id);
  if (!lane) {
    throw new Error(`No swimlane ${id}`);
  }
  return lane;
}

function problem(status: number, detail: string): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: { detail } });
}
