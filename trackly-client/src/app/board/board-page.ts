import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { Swimlane, Ticket } from '../core/board.models';
import { BoardStore } from '../core/board.store';
import { ConfirmDialog, ConfirmData } from './dialogs/confirm.dialog';
import { AssignTicketDialog, AssignTicketData } from './dialogs/assign-ticket.dialog';
import { TextPromptData, TextPromptDialog } from './dialogs/text-prompt.dialog';
import {
  TicketFormData,
  TicketFormDialog,
  TicketFormResult,
} from './dialogs/ticket-form.dialog';
import { SwimlaneColumn, TicketDrop } from './swimlane-column';

@Component({
  selector: 'app-board-page',
  imports: [
    DragDropModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    SwimlaneColumn,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './board-page.html',
  styleUrl: './board-page.css',
})
export class BoardPage {
  private readonly store = inject(BoardStore);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  protected readonly swimlanes = this.store.swimlanes;
  protected readonly loading = this.store.loading;
  protected readonly loadError = this.store.loadError;
  protected readonly hasBoard = this.store.hasBoard;
  protected readonly connectedListIds = this.store.swimlaneListIds;
  protected readonly isAdmin = this.auth.isAdmin;

  protected readonly lastIndex = computed(() => this.swimlanes().length - 1);

  protected async addSwimlane(): Promise<void> {
    const title = await this.prompt({
      title: 'Add swimlane',
      label: 'Swimlane title',
      confirmLabel: 'Add swimlane',
      testId: 'swimlane-title-input',
    });
    if (title) {
      await this.store.addSwimlane(title);
    }
  }

  protected async deleteSwimlane(swimlane: Swimlane): Promise<void> {
    const confirmed = await this.confirm({
      title: `Delete "${swimlane.title}"?`,
      message: 'This swimlane will be removed from the board. This cannot be undone.',
      confirmLabel: 'Delete swimlane',
      destructive: true,
    });
    if (confirmed) {
      await this.store.deleteSwimlane(swimlane.id);
    }
  }

  protected onSwimlaneDropped(event: CdkDragDrop<readonly Swimlane[]>): void {
    void this.store.moveSwimlane(event.previousIndex, event.currentIndex);
  }

  protected moveSwimlaneLeft(swimlane: Swimlane): void {
    const index = this.indexOf(swimlane);
    void this.store.moveSwimlane(index, index - 1);
  }

  protected moveSwimlaneRight(swimlane: Swimlane): void {
    const index = this.indexOf(swimlane);
    void this.store.moveSwimlane(index, index + 1);
  }

  protected onTicketMoved(drop: TicketDrop): void {
    void this.store.moveTicket(drop.ticketId, drop.fromSwimlaneId, drop.toSwimlaneId, drop.toIndex);
  }

  protected async addTicket(swimlane: Swimlane): Promise<void> {
    const data: TicketFormData = { swimlaneTitle: swimlane.title, users: this.store.users() };
    const result = await firstValueFrom(
      this.dialog
        .open<TicketFormDialog, TicketFormData, TicketFormResult>(TicketFormDialog, { data })
        .afterClosed(),
    );
    if (!result) {
      return;
    }

    await this.store.createTicket(swimlane.id, result.title, result.description);

    // Creating and assigning are two calls to board-service, but one action to the user.
    if (result.assigneeId) {
      const created = this.findTicketByTitle(swimlane.id, result.title);
      if (created) {
        await this.store.assignTicket(created.id, result.assigneeId);
      }
    }
  }

  protected async assignTicket(ticket: Ticket): Promise<void> {
    const data: AssignTicketData = {
      ticketTitle: ticket.title,
      currentAssigneeId: ticket.assigneeId,
      users: this.store.users(),
    };
    const assigneeId = await firstValueFrom(
      this.dialog
        .open<AssignTicketDialog, AssignTicketData, string>(AssignTicketDialog, { data })
        .afterClosed(),
    );
    if (assigneeId) {
      await this.store.assignTicket(ticket.id, assigneeId);
    }
  }

  protected reload(): void {
    void this.store.load();
  }

  private indexOf(swimlane: Swimlane): number {
    return this.swimlanes().findIndex((lane) => lane.id === swimlane.id);
  }

  /** The newest match, since the ticket that was just appended is the one to assign. */
  private findTicketByTitle(swimlaneId: number, title: string): Ticket | undefined {
    const lane = this.swimlanes().find((candidate) => candidate.id === swimlaneId);
    return lane?.tickets.filter((ticket) => ticket.title === title).at(-1);
  }

  private async prompt(data: TextPromptData): Promise<string | undefined> {
    return firstValueFrom(
      this.dialog
        .open<TextPromptDialog, TextPromptData, string>(TextPromptDialog, { data })
        .afterClosed(),
    );
  }

  private async confirm(data: ConfirmData): Promise<boolean | undefined> {
    return firstValueFrom(
      this.dialog
        .open<ConfirmDialog, ConfirmData, boolean>(ConfirmDialog, {
          data,
          // Cancel comes first in the dialog, so the default 'first-tabbable' would land there;
          // the confirming button is the one the keyboard should start on.
          autoFocus: '[data-testid="confirm-accept"]',
        })
        .afterClosed(),
    );
  }
}
