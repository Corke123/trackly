import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { User } from '../../core/board.models';

export interface AssignTicketData {
  readonly ticketTitle: string;
  readonly currentAssigneeId: string | null;
  readonly users: readonly User[];
}

@Component({
  selector: 'app-assign-ticket-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Assign "{{ data.ticketTitle }}"</h2>

    <mat-dialog-content>
      <mat-form-field class="assign-field">
        <mat-label>Assignee</mat-label>
        <mat-select [formControl]="assignee" data-testid="assignee-select">
          @for (user of data.users; track user.username) {
            <mat-option [value]="user.username">{{ user.username }}</mat-option>
          }
        </mat-select>
        @if (data.users.length === 0) {
          <mat-hint>No users are available to assign right now.</mat-hint>
        }
      </mat-form-field>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button matButton type="button" data-testid="assign-cancel" (click)="cancel()">Cancel</button>
      <button
        matButton="filled"
        type="button"
        data-testid="assign-submit"
        [disabled]="assignee.invalid"
        (click)="submit()"
      >
        Assign
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .assign-field {
      min-width: min(20rem, 70vw);
    }
  `,
})
export class AssignTicketDialog {
  protected readonly data = inject<AssignTicketData>(MAT_DIALOG_DATA);

  private readonly dialogRef = inject<MatDialogRef<AssignTicketDialog, string>>(MatDialogRef);

  protected readonly assignee = new FormControl<string | null>(this.data.currentAssigneeId, {
    validators: [Validators.required],
  });

  protected submit(): void {
    const value = this.assignee.value;
    if (value === null) {
      this.assignee.markAsTouched();
      return;
    }
    this.dialogRef.close(value);
  }

  protected cancel(): void {
    this.dialogRef.close();
  }
}
