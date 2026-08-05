import { ANIMATION_MODULE_TYPE, Type, provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { someUsers } from '../../../testing/board.fixtures';
import { click, query, type } from '../../../testing/dom';
import { provideTracklyIcons } from '../../core/icons';
import { AssignTicketData, AssignTicketDialog } from './assign-ticket.dialog';
import { TextPromptData, TextPromptDialog } from './text-prompt.dialog';
import { TicketFormData, TicketFormDialog } from './ticket-form.dialog';

const close = vi.fn();

async function build<T>(component: Type<T>, data: unknown) {
  close.mockClear();
  await TestBed.configureTestingModule({
    imports: [component],
    providers: [
      provideZonelessChangeDetection(),
      { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
      provideTracklyIcons(),
      { provide: MAT_DIALOG_DATA, useValue: data },
      { provide: MatDialogRef, useValue: { close } },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(component);
  await fixture.whenStable();
  return fixture;
}

describe('TextPromptDialog', () => {
  const data: TextPromptData = {
    title: 'Rename board',
    label: 'Board name',
    confirmLabel: 'Rename',
    initialValue: 'Trackly Board',
    testId: 'board-name-input',
  };

  let fixture: ComponentFixture<TextPromptDialog>;

  beforeEach(async () => {
    fixture = await build(TextPromptDialog, data);
  });

  it('opens with the current value, so a rename is an edit rather than a retype', () => {
    expect((query(fixture, 'board-name-input') as HTMLInputElement).value).toBe('Trackly Board');
  });

  it('returns the new value', async () => {
    type(fixture, 'board-name-input', 'Release board');
    await fixture.whenStable();

    click(fixture, 'text-prompt-confirm');

    expect(close).toHaveBeenCalledWith('Release board');
  });

  it('trims what the user typed, since the services reject blank names', async () => {
    type(fixture, 'board-name-input', '  Release board  ');
    await fixture.whenStable();

    click(fixture, 'text-prompt-confirm');

    expect(close).toHaveBeenCalledWith('Release board');
  });

  it('refuses a name that is only whitespace, without asking the server', async () => {
    type(fixture, 'board-name-input', '   ');
    await fixture.whenStable();

    click(fixture, 'text-prompt-confirm');

    expect(close).not.toHaveBeenCalled();
  });

  it('returns nothing when cancelled', () => {
    click(fixture, 'text-prompt-cancel');

    expect(close).toHaveBeenCalledWith();
  });
});

describe('TicketFormDialog', () => {
  const data: TicketFormData = { swimlaneTitle: 'To Do', users: someUsers() };

  let fixture: ComponentFixture<TicketFormDialog>;

  beforeEach(async () => {
    fixture = await build(TicketFormDialog, data);
  });

  it('says which swimlane the ticket is being added to', () => {
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('New ticket in To Do');
  });

  it('returns the ticket that was described', async () => {
    type(fixture, 'ticket-title', 'Write tests');
    type(fixture, 'ticket-description', 'Cover the happy paths');
    await fixture.whenStable();

    click(fixture, 'ticket-submit');

    expect(close).toHaveBeenCalledWith({
      title: 'Write tests',
      description: 'Cover the happy paths',
      assigneeId: null,
    });
  });

  it('treats an empty description as no description at all', async () => {
    type(fixture, 'ticket-title', 'Write tests');
    await fixture.whenStable();

    click(fixture, 'ticket-submit');

    expect(close).toHaveBeenCalledWith({
      title: 'Write tests',
      description: null,
      assigneeId: null,
    });
  });

  it('will not create a ticket with no title', () => {
    click(fixture, 'ticket-submit');

    expect(close).not.toHaveBeenCalled();
  });

  it('will not create a ticket whose title is only whitespace', async () => {
    type(fixture, 'ticket-title', '   ');
    await fixture.whenStable();

    click(fixture, 'ticket-submit');

    expect(close).not.toHaveBeenCalled();
  });
});

describe('AssignTicketDialog', () => {
  const data: AssignTicketData = {
    ticketTitle: 'Write tests',
    currentAssigneeId: 'demo',
    users: someUsers(),
  };

  it('offers the users identity-service knows about', async () => {
    const fixture = await build(AssignTicketDialog, data);

    expect(query(fixture, 'assignee-select')).not.toBeNull();
  });

  it('returns the assignee that was already set when it is confirmed unchanged', async () => {
    const fixture = await build(AssignTicketDialog, data);

    click(fixture, 'assign-submit');

    expect(close).toHaveBeenCalledWith('demo');
  });

  it('assigns nobody when there is nobody to assign and nothing was chosen', async () => {
    const fixture = await build(AssignTicketDialog, {
      ...data,
      currentAssigneeId: null,
      users: [],
    });

    click(fixture, 'assign-submit');

    expect(close).not.toHaveBeenCalled();
  });

  it('returns nothing when cancelled', async () => {
    const fixture = await build(AssignTicketDialog, data);

    click(fixture, 'assign-cancel');

    expect(close).toHaveBeenCalledWith();
  });
});
