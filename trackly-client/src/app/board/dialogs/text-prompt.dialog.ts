import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface TextPromptData {
  readonly title: string;
  readonly label: string;
  readonly confirmLabel: string;
  readonly initialValue?: string;
  readonly testId?: string;
}

/**
 * One field, one answer — renaming the board and adding a swimlane are the same interaction, and a
 * shared dialog keeps them behaving identically.
 */
@Component({
  selector: 'app-text-prompt-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>

    <mat-dialog-content>
      <mat-form-field class="w-full">
        <mat-label>{{ data.label }}</mat-label>
        <input
          matInput
          [formControl]="value"
          [attr.data-testid]="data.testId ?? 'text-prompt-input'"
          (keydown.enter)="confirm()"
        />
        @if (value.hasError('required') && value.touched) {
          <mat-error>{{ data.label }} is required.</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button matButton type="button" data-testid="text-prompt-cancel" (click)="cancel()">
        Cancel
      </button>
      <button
        matButton="filled"
        type="button"
        data-testid="text-prompt-confirm"
        [disabled]="value.invalid"
        (click)="confirm()"
      >
        {{ data.confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
})
export class TextPromptDialog {
  protected readonly data = inject<TextPromptData>(MAT_DIALOG_DATA);

  private readonly dialogRef = inject<MatDialogRef<TextPromptDialog, string>>(MatDialogRef);

  protected readonly value = new FormControl(this.data.initialValue ?? '', {
    nonNullable: true,
    validators: [Validators.required, blankValidator],
  });

  protected confirm(): void {
    if (this.value.invalid) {
      this.value.markAsTouched();
      return;
    }
    this.dialogRef.close(this.value.value.trim());
  }

  protected cancel(): void {
    this.dialogRef.close();
  }
}

/** The services reject blank names too; catching it here saves a pointless round trip. */
function blankValidator(control: { value: string }) {
  return control.value.trim().length === 0 ? { required: true } : null;
}
