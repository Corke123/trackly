import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  notify(message: string): void {
    this.snackBar.open(message, 'Dismiss', { duration: 4000 });
  }

  /** Failures stay up until dismissed — they usually ask the user to do something differently. */
  reportError(message: string): void {
    this.snackBar.open(message, 'Dismiss', { politeness: 'assertive' });
  }
}
