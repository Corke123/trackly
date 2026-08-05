import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { swimlaneListId } from '../core/board.store';
import { Swimlane, Ticket } from '../core/board.models';
import { MoveTicketRequest, TicketCard } from './ticket-card';

/** A ticket landing in this lane, carrying where it came from. */
export interface TicketDrop {
  readonly ticketId: number;
  readonly fromSwimlaneId: number;
  readonly toSwimlaneId: number;
  readonly toIndex: number;
}

@Component({
  selector: 'app-swimlane-column',
  imports: [
    DragDropModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    TicketCard,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './swimlane-column.html',
  styleUrl: './swimlane-column.css',
})
export class SwimlaneColumn {
  readonly swimlane = input.required<Swimlane>();

  /** Admins get the lane controls; everyone gets the tickets. */
  readonly isAdmin = input<boolean>(false);

  readonly allSwimlanes = input<readonly Swimlane[]>([]);

  /** Mutable because the CDK's `cdkDropListConnectedTo` input demands a mutable array. */
  readonly connectedListIds = input<string[]>([]);

  readonly canMoveLeft = input<boolean>(false);

  readonly canMoveRight = input<boolean>(false);

  readonly addTicket = output<Swimlane>();

  readonly deleteSwimlane = output<Swimlane>();

  readonly moveSwimlaneLeft = output<Swimlane>();

  readonly moveSwimlaneRight = output<Swimlane>();

  readonly assignTicket = output<Ticket>();

  readonly moveTicket = output<TicketDrop>();

  protected readonly listId = computed(() => swimlaneListId(this.swimlane().id));

  protected readonly tickets = computed(() => this.swimlane().tickets);

  protected readonly ticketCount = computed(() => this.swimlane().tickets.length);

  protected readonly otherSwimlanes = computed(() =>
    this.allSwimlanes().filter((lane) => lane.id !== this.swimlane().id),
  );

  /**
   * A lane with tickets on it cannot be deleted — board-service refuses, and saying so up front is
   * kinder than a failed request.
   */
  protected readonly canDelete = computed(() => this.ticketCount() === 0);

  protected readonly deleteTooltip = computed(() =>
    this.canDelete() ? 'Delete swimlane' : 'Move its tickets elsewhere before deleting this lane',
  );

  protected onTicketDropped(event: CdkDragDrop<Swimlane>): void {
    const ticket = event.item.data as Ticket;
    this.moveTicket.emit({
      ticketId: ticket.id,
      fromSwimlaneId: event.previousContainer.data.id,
      toSwimlaneId: event.container.data.id,
      toIndex: event.currentIndex,
    });
  }

  protected onMoveRequested(request: MoveTicketRequest): void {
    this.moveTicket.emit({
      ticketId: request.ticketId,
      fromSwimlaneId: this.swimlane().id,
      toSwimlaneId: request.toSwimlaneId,
      toIndex: 0,
    });
  }
}
