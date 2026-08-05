import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { User } from '../../core/board.models';

export interface TicketFormData {
  readonly swimlaneTitle: string;
  readonly users: readonly User[];
}

export interface TicketFormResult {
  readonly title: string;
  readonly description: string | null;
  readonly assigneeId: string | null;
}

/** Creating a ticket and assigning it are one step here — the common case is both at once. */
@Component({
  selector: 'app-ticket-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>New ticket in {{ data.swimlaneTitle }}</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="ticket-form">
        <mat-form-field>
          <mat-label>Title</mat-label>
          <input matInput formControlName="title" data-testid="ticket-title" />
          @if (form.controls.title.hasError('required') && form.controls.title.touched) {
            <mat-error>A title is required.</mat-error>
          }
        </mat-form-field>

        <mat-form-field>
          <mat-label>Description</mat-label>
          <textarea
            matInput
            rows="4"
            formControlName="description"
            data-testid="ticket-description"
          ></textarea>
        </mat-form-field>

        <mat-form-field>
          <mat-label>Assignee</mat-label>
          <mat-select formControlName="assigneeId" data-testid="ticket-assignee">
            <mat-option [value]="null">Unassigned</mat-option>
            @for (user of data.users; track user.username) {
              <mat-option [value]="user.username">{{ user.username }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button matButton type="button" data-testid="ticket-cancel" (click)="cancel()">Cancel</button>
      <button
        matButton="filled"
        type="button"
        data-testid="ticket-submit"
        [disabled]="form.invalid"
        (click)="submit()"
      >
        Create ticket
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .ticket-form {
      display: flex;
      flex-direction: column;
      min-width: min(24rem, 70vw);
    }
  `,
})
export class TicketFormDialog {
  protected readonly data = inject<TicketFormData>(MAT_DIALOG_DATA);

  private readonly dialogRef =
    inject<MatDialogRef<TicketFormDialog, TicketFormResult>>(MatDialogRef);

  protected readonly form = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    description: new FormControl('', { nonNullable: true }),
    assigneeId: new FormControl<string | null>(null),
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { title, description, assigneeId } = this.form.getRawValue();
    const trimmedTitle = title.trim();
    if (trimmedTitle.length === 0) {
      this.form.controls.title.setErrors({ required: true });
      this.form.markAllAsTouched();
      return;
    }

    this.dialogRef.close({
      title: trimmedTitle,
      description: description.trim().length === 0 ? null : description.trim(),
      assigneeId,
    });
  }

  protected cancel(): void {
    this.dialogRef.close();
  }
}
