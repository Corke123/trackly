import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { auditTime, filter, merge } from 'rxjs';
import { ActivityStreamService } from './activity-stream.service';
import { AuthService } from './auth.service';
import { BoardStore } from './board.store';

export const REFRESH_WINDOW = 250;

@Injectable({ providedIn: 'root' })
export class LiveBoardService {
  private readonly stream = inject(ActivityStreamService);
  private readonly auth = inject(AuthService);
  private readonly store = inject(BoardStore);
  private readonly destroyRef = inject(DestroyRef);

  private started = false;

  start(): void {
    if (this.started) {
      return;
    }
    this.started = true;

    merge(
      this.stream.boardChanges.pipe(filter((change) => change.actorId !== this.auth.username())),
      this.stream.reconnects,
    )
      .pipe(auditTime(REFRESH_WINDOW), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => void this.store.refresh());
  }
}
