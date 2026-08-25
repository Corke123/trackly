import { ANIMATION_MODULE_TYPE, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { click, clickOverlay, query, queryOverlay } from '../../testing/dom';
import { provideTracklyIcons } from '../core/icons';
import { aBoard, someUsers } from '../../testing/board.fixtures';
import { AuthService } from '../core/auth.service';
import { BoardStore } from '../core/board.store';
import { BoardPage } from './board-page';

describe('BoardPage', () => {
  let store: ReturnType<typeof fakeStore>;
  let isAdmin: ReturnType<typeof signal<boolean>>;
  let dialogResult: unknown;
  let openedDialogs: unknown[];
  let fixture: ComponentFixture<BoardPage>;

  function fakeStore() {
    const board = signal(aBoard());
    return {
      swimlanes: signal(board().swimlanes),
      swimlaneListIds: signal(['swimlane-10', 'swimlane-20', 'swimlane-30']),
      users: signal(someUsers()),
      loading: signal(false),
      loadError: signal<string | null>(null),
      hasBoard: signal(true),
      load: vi.fn(),
      addSwimlane: vi.fn(),
      deleteSwimlane: vi.fn(),
      moveSwimlane: vi.fn(),
      createTicket: vi.fn(),
      moveTicket: vi.fn(),
      assignTicket: vi.fn(),
      deleteTicket: vi.fn(),
    };
  }

  beforeEach(async () => {
    store = fakeStore();
    isAdmin = signal(false);
    dialogResult = undefined;
    openedDialogs = [];

    await TestBed.configureTestingModule({
      imports: [BoardPage],
      providers: [
        provideZonelessChangeDetection(),
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        provideTracklyIcons(),
        { provide: BoardStore, useValue: store },
        { provide: AuthService, useValue: { isAdmin } },
        {
          provide: MatDialog,
          useValue: {
            open: (component: unknown) => {
              openedDialogs.push(component);
              return { afterClosed: () => of(dialogResult) };
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BoardPage);
    await fixture.whenStable();
  });

  async function asAdmin(): Promise<void> {
    isAdmin.set(true);
    await fixture.whenStable();
  }

  describe('what a plain user sees', () => {
    it('renders every swimlane on the board', () => {
      expect(query(fixture, 'swimlane-10')).not.toBeNull();
      expect(query(fixture, 'swimlane-20')).not.toBeNull();
      expect(query(fixture, 'swimlane-30')).not.toBeNull();
    });

    it('renders the tickets in their swimlanes', () => {
      expect(query(fixture, 'ticket-100')).not.toBeNull();
      expect(query(fixture, 'ticket-200')).not.toBeNull();
    });

    it('can add a ticket to any swimlane', () => {
      expect(query(fixture, 'add-ticket-10')).not.toBeNull();
    });

    it('is offered no way to add a swimlane', () => {
      expect(query(fixture, 'add-swimlane')).toBeNull();
    });

    it('is offered no way to delete a ticket', async () => {
      query(fixture, 'ticket-menu-100')?.click();
      await fixture.whenStable();

      expect(queryOverlay('assign-ticket-100')).not.toBeNull();
      expect(queryOverlay('delete-ticket-100')).toBeNull();
    });

    it('is offered no swimlane actions at all', () => {
      expect(query(fixture, 'swimlane-menu-10')).toBeNull();
      expect(query(fixture, 'swimlane-handle-10')).toBeNull();
    });
  });

  describe('what an admin sees', () => {
    it('can add a swimlane', async () => {
      await asAdmin();

      expect(query(fixture, 'add-swimlane')).not.toBeNull();
    });

    it('gets the swimlane actions and the drag handle', async () => {
      await asAdmin();

      expect(query(fixture, 'swimlane-menu-10')).not.toBeNull();
      expect(query(fixture, 'swimlane-handle-10')).not.toBeNull();
    });

    it('adds a swimlane with the title it was given', async () => {
      await asAdmin();
      dialogResult = 'Blocked';

      query(fixture, 'add-swimlane')?.click();
      await fixture.whenStable();

      expect(store.addSwimlane).toHaveBeenCalledWith('Blocked');
    });

    it('adds nothing when the add dialog is dismissed', async () => {
      await asAdmin();
      dialogResult = undefined;

      query(fixture, 'add-swimlane')?.click();
      await fixture.whenStable();

      expect(store.addSwimlane).not.toHaveBeenCalled();
    });

    it('deletes a swimlane only after it has been confirmed', async () => {
      await asAdmin();
      dialogResult = true;

      await openLaneMenu(fixture, 30);
      clickOverlay('delete-swimlane-30');
      await fixture.whenStable();

      expect(store.deleteSwimlane).toHaveBeenCalledWith(30);
    });

    it('keeps the swimlane when the confirmation is declined', async () => {
      await asAdmin();
      dialogResult = false;

      await openLaneMenu(fixture, 30);
      clickOverlay('delete-swimlane-30');
      await fixture.whenStable();

      expect(store.deleteSwimlane).not.toHaveBeenCalled();
    });

    it('will not offer to delete a swimlane that still holds tickets', async () => {
      await asAdmin();

      await openLaneMenu(fixture, 10);
      const deleteItem = queryOverlay('delete-swimlane-10');

      expect(deleteItem?.hasAttribute('disabled')).toBe(true);
    });

    it('deletes a ticket only after it has been confirmed', async () => {
      await asAdmin();
      dialogResult = true;

      query(fixture, 'ticket-menu-100')?.click();
      await fixture.whenStable();
      clickOverlay('delete-ticket-100');
      await fixture.whenStable();

      expect(store.deleteTicket).toHaveBeenCalledWith(100);
    });

    it('keeps the ticket when the confirmation is declined', async () => {
      await asAdmin();
      dialogResult = false;

      query(fixture, 'ticket-menu-100')?.click();
      await fixture.whenStable();
      clickOverlay('delete-ticket-100');
      await fixture.whenStable();

      expect(store.deleteTicket).not.toHaveBeenCalled();
    });

    it('moves a swimlane one place to the right from its menu', async () => {
      await asAdmin();

      await openLaneMenu(fixture, 10);
      clickOverlay('swimlane-move-right-10');
      await fixture.whenStable();

      expect(store.moveSwimlane).toHaveBeenCalledWith(0, 1);
    });

    it('moves a swimlane one place to the left from its menu', async () => {
      await asAdmin();

      await openLaneMenu(fixture, 20);
      clickOverlay('swimlane-move-left-20');
      await fixture.whenStable();

      expect(store.moveSwimlane).toHaveBeenCalledWith(1, 0);
    });

    it('cannot move the first swimlane further left', async () => {
      await asAdmin();

      await openLaneMenu(fixture, 10);

      expect(queryOverlay('swimlane-move-left-10')?.hasAttribute('disabled')).toBe(true);
    });

    it('cannot move the last swimlane further right', async () => {
      await asAdmin();

      await openLaneMenu(fixture, 30);

      expect(queryOverlay('swimlane-move-right-30')?.hasAttribute('disabled')).toBe(true);
    });
  });

  describe('tickets', () => {
    it('creates a ticket from the dialog it was described in', async () => {
      dialogResult = { title: 'Third', description: 'Details', assigneeId: null };

      query(fixture, 'add-ticket-30')?.click();
      await fixture.whenStable();

      expect(store.createTicket).toHaveBeenCalledWith(30, 'Third', 'Details');
      expect(store.assignTicket).not.toHaveBeenCalled();
    });

    it('creates nothing when the ticket dialog is dismissed', async () => {
      dialogResult = undefined;

      query(fixture, 'add-ticket-30')?.click();
      await fixture.whenStable();

      expect(store.createTicket).not.toHaveBeenCalled();
    });

    it('assigns a ticket that was created with an assignee, in one action', async () => {
      dialogResult = { title: 'First', description: null, assigneeId: 'demo' };

      query(fixture, 'add-ticket-10')?.click();
      await fixture.whenStable();

      expect(store.createTicket).toHaveBeenCalledWith(10, 'First', null);
      expect(store.assignTicket).toHaveBeenCalledWith(100, 'demo');
    });

    it('assigns an existing ticket from its menu', async () => {
      dialogResult = 'user';

      query(fixture, 'ticket-menu-100')?.click();
      await fixture.whenStable();
      clickOverlay('assign-ticket-100');
      await fixture.whenStable();

      expect(store.assignTicket).toHaveBeenCalledWith(100, 'user');
    });

    it('moves a ticket to another swimlane from its menu, without a pointer', async () => {
      query(fixture, 'ticket-menu-100')?.click();
      await fixture.whenStable();
      clickOverlay('move-ticket-menu-100');
      await fixture.whenStable();
      clickOverlay('move-ticket-100-to-20');
      await fixture.whenStable();

      expect(store.moveTicket).toHaveBeenCalledWith(100, 10, 20, 0);
    });

    it('does not offer to move a ticket to the swimlane it is already on', async () => {
      query(fixture, 'ticket-menu-100')?.click();
      await fixture.whenStable();
      clickOverlay('move-ticket-menu-100');
      await fixture.whenStable();

      expect(queryOverlay('move-ticket-100-to-10')).toBeNull();
      expect(queryOverlay('move-ticket-100-to-20')).not.toBeNull();
    });

    it('shows who a ticket is assigned to', () => {
      expect(query(fixture, 'ticket-assignee-200')?.textContent).toContain('demo');
    });

    it('says so when a ticket has no assignee', () => {
      expect(query(fixture, 'ticket-100')?.textContent).toContain('Unassigned');
    });
  });

  describe('when the board cannot be shown', () => {
    it('explains the failure and offers to try again', async () => {
      store.loadError.set('The board could not be loaded.');
      store.hasBoard.set(false);
      await fixture.whenStable();

      expect(query(fixture, 'board-error')?.textContent).toContain(
        'The board could not be loaded.',
      );

      query(fixture, 'board-retry')?.click();
      expect(store.load).toHaveBeenCalled();
    });

    it('says a board with no swimlanes is empty rather than showing nothing', async () => {
      store.swimlanes.set([]);
      await fixture.whenStable();

      expect(query(fixture, 'board-empty')).not.toBeNull();
    });
  });
});

/** The lane menu is rendered into an overlay, so its items live outside the fixture's element. */
async function openLaneMenu(fixture: ComponentFixture<BoardPage>, swimlaneId: number) {
  click(fixture, `swimlane-menu-${swimlaneId}`);
  await fixture.whenStable();
}
