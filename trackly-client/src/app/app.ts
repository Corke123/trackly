import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { RouterOutlet } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { TextPromptData, TextPromptDialog } from './board/dialogs/text-prompt.dialog';
import { ActivityStreamService } from './core/activity-stream.service';
import { AuthService } from './core/auth.service';
import { BoardStore } from './core/board.store';
import { LiveBoardService } from './core/live-board.service';
import { NotificationService } from './core/notification.service';
import { Header } from './shell/header';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Header],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly store = inject(BoardStore);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly activityStream = inject(ActivityStreamService);
  private readonly liveBoard = inject(LiveBoardService);
  private readonly notifications = inject(NotificationService);

  protected readonly boardName = this.store.boardName;
  protected readonly isAdmin = this.auth.isAdmin;

  constructor() {
    this.activityStream.notifications
      .pipe(takeUntilDestroyed())
      .subscribe((notification) => this.notifications.announce(notification.message));
    this.liveBoard.start();
    this.activityStream.connect();

    void this.start();
  }

  protected async renameBoard(): Promise<void> {
    const data: TextPromptData = {
      title: 'Rename board',
      label: 'Board name',
      confirmLabel: 'Rename',
      initialValue: this.boardName(),
      testId: 'board-name-input',
    };

    const name = await firstValueFrom(
      this.dialog
        .open<TextPromptDialog, TextPromptData, string>(TextPromptDialog, { data })
        .afterClosed(),
    );
    if (name) {
      await this.store.renameBoard(name);
    }
  }

  /**
   * Who is signed in decides which controls render, but `isAdmin` is a signal the header and board
   * pick up whenever it arrives — so the two requests go out together rather than in a chain.
   *
   * A failed identity lookup is not fatal: an expired session is already being handled by the
   * interceptor, and anything else still leaves a readable board.
   */
  private async start(): Promise<void> {
    await Promise.all([this.auth.load().catch(() => undefined), this.store.load()]);
  }
}
