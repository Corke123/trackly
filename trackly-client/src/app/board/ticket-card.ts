import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Swimlane, Ticket } from '../core/board.models';

/** Where a ticket can be sent from its own menu, for anyone not using a pointer. */
export interface MoveTicketRequest {
  readonly ticketId: number;
  readonly toSwimlaneId: number;
}

@Component({
  selector: 'app-ticket-card',
  imports: [MatButtonModule, MatIconModule, MatMenuModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ticket-card.html',
  styleUrl: './ticket-card.css',
})
export class TicketCard {
  readonly ticket = input.required<Ticket>();

  /** Every other swimlane, so the card can offer somewhere to move to. */
  readonly otherSwimlanes = input<readonly Swimlane[]>([]);

  readonly isAdmin = input<boolean>(false);

  readonly assign = output<Ticket>();

  readonly delete = output<Ticket>();

  readonly move = output<MoveTicketRequest>();

  protected requestMove(toSwimlaneId: number): void {
    this.move.emit({ ticketId: this.ticket().id, toSwimlaneId });
  }
}
