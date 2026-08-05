import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL, LOGOUT_URL } from './api.config';
import { CurrentUser } from './board.models';

/**
 * The browser holds only the gateway's session cookie (ADR 0005), so the SPA asks the gateway who
 * is signed in instead of reading a token.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);
  private readonly baseUrl = inject(API_BASE_URL);

  private readonly user = signal<CurrentUser | null>(null);

  readonly currentUser = this.user.asReadonly();

  readonly username = computed(() => this.user()?.username ?? '');

  readonly isAdmin = computed(() => this.user()?.admin ?? false);

  async load(): Promise<CurrentUser> {
    const user = await firstValueFrom(this.http.get<CurrentUser>(`${this.baseUrl}/me`));
    this.user.set(user);
    return user;
  }

  /**
   * Logging out has to leave the SPA: the gateway ends the session, then hands over to the
   * authorization server's end-session endpoint and back. A form POST carries the CSRF token the
   * gateway requires while still being a navigation the browser can follow.
   */
  logout(): void {
    const form = this.document.createElement('form');
    form.method = 'post';
    form.action = LOGOUT_URL;
    form.style.display = 'none';

    const token = this.readCsrfToken();
    if (token) {
      const input = this.document.createElement('input');
      input.type = 'hidden';
      input.name = '_csrf';
      input.value = token;
      form.appendChild(input);
    }

    this.document.body.appendChild(form);
    form.submit();
  }

  private readCsrfToken(): string | null {
    const match = this.document.cookie?.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : null;
  }
}
