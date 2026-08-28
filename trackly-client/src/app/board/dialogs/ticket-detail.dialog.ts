import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { auditTime, filter, firstValueFrom } from 'rxjs';
import { ActivityStreamService } from '../../core/activity-stream.service';
import { AuthService } from '../../core/auth.service';
import { BoardApiService } from '../../core/board-api.service';
import { Comment, Ticket } from '../../core/board.models';
import { REFRESH_WINDOW } from '../../core/live-board.service';
import { describeError } from '../../core/problem-detail';

export const TICKET_COMMENTED = 'TicketCommented';

export const COMMENT_MAX_LENGTH = 2000;

export interface TicketDetailData {
  readonly ticket: Ticket;
}

@Component({
  selector: 'app-ticket-detail-dialog',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ticket-detail.dialog.html',
  styleUrl: './ticket-detail.dialog.css',
})
export class TicketDetailDialog {
  protected readonly data = inject<TicketDetailData>(MAT_DIALOG_DATA);

  private readonly dialogRef = inject<MatDialogRef<TicketDetailDialog, void>>(MatDialogRef);
  private readonly api = inject(BoardApiService);
  private readonly auth = inject(AuthService);
  private readonly stream = inject(ActivityStreamService);

  private readonly thread = signal<readonly Comment[]>([]);
  private readonly loadingState = signal<boolean>(true);
  private readonly errorState = signal<string | null>(null);
  private readonly postingState = signal<boolean>(false);

  protected readonly comments = this.thread.asReadonly();
  protected readonly loading = this.loadingState.asReadonly();
  protected readonly error = this.errorState.asReadonly();
  protected readonly posting = this.postingState.asReadonly();

  protected readonly maxLength = COMMENT_MAX_LENGTH;

  protected readonly body = new FormControl<string>('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(COMMENT_MAX_LENGTH)],
  });

  protected readonly canPost = computed(() => !this.posting());

  constructor() {
    void this.load();

    this.stream.boardChanges
      .pipe(
        filter(
          (change) => change.type === TICKET_COMMENTED && change.actorId !== this.auth.username(),
        ),
        auditTime(REFRESH_WINDOW),
        takeUntilDestroyed(),
      )
      .subscribe(() => void this.load(false));
  }

  protected mine(comment: Comment): boolean {
    return comment.authorId === this.auth.username();
  }

  protected removable(comment: Comment): boolean {
    return this.auth.isAdmin() || this.mine(comment);
  }

  protected async post(): Promise<void> {
    const body = this.body.value.trim();
    if (body.length === 0 || body.length > COMMENT_MAX_LENGTH) {
      this.body.markAsTouched();
      return;
    }

    this.postingState.set(true);
    try {
      const created = await firstValueFrom(this.api.postComment(this.data.ticket.id, body));
      this.thread.update((comments) => [...comments, created]);
      this.body.reset();
      this.errorState.set(null);
    } catch (error) {
      this.errorState.set(describeError(error, 'The comment could not be posted.'));
    } finally {
      this.postingState.set(false);
    }
  }

  protected async remove(comment: Comment): Promise<void> {
    try {
      await firstValueFrom(this.api.deleteComment(this.data.ticket.id, comment.id));
      this.thread.update((comments) => comments.filter((other) => other.id !== comment.id));
      this.errorState.set(null);
    } catch (error) {
      this.errorState.set(describeError(error, 'The comment could not be deleted.'));
    }
  }

  protected close(): void {
    this.dialogRef.close();
  }

  private async load(announce = true): Promise<void> {
    if (announce) {
      this.loadingState.set(true);
    }
    try {
      this.thread.set(await firstValueFrom(this.api.listComments(this.data.ticket.id)));
      this.errorState.set(null);
    } catch (error) {
      this.errorState.set(describeError(error, 'The comments could not be loaded.'));
    } finally {
      this.loadingState.set(false);
    }
  }
}
