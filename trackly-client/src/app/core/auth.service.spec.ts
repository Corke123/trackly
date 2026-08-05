import { DOCUMENT } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { aPlainUser, anAdmin } from '../../testing/board.fixtures';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AuthService],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks the gateway who is signed in, since the browser holds no token', async () => {
    const loading = auth.load();
    http.expectOne({ method: 'GET', url: '/api/me' }).flush(anAdmin());

    await loading;

    expect(auth.username()).toBe('admin');
    expect(auth.isAdmin()).toBe(true);
  });

  it('reports a user without the admin role as not an admin', async () => {
    const loading = auth.load();
    http.expectOne({ method: 'GET', url: '/api/me' }).flush(aPlainUser());

    await loading;

    expect(auth.username()).toBe('demo');
    expect(auth.isAdmin()).toBe(false);
  });

  it('claims nothing about the user before anyone has been loaded', () => {
    expect(auth.currentUser()).toBeNull();
    expect(auth.isAdmin()).toBe(false);
    expect(auth.username()).toBe('');
  });
});

describe('AuthService logout', () => {
  const submit = vi.fn();
  let auth: AuthService;

  beforeEach(() => {
    submit.mockClear();
    // jsdom does not implement form submission, and calling it would navigate the test runner away.
    Object.defineProperty(HTMLFormElement.prototype, 'submit', {
      value: submit,
      configurable: true,
    });

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        AuthService,
        { provide: DOCUMENT, useValue: document },
      ],
    });
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    document.querySelectorAll('form').forEach((form) => form.remove());
    document.cookie = 'XSRF-TOKEN=; Max-Age=0';
  });

  it('leaves the app with a form POST so the OIDC logout redirects can be followed', () => {
    auth.logout();

    const form = document.querySelector('form');
    expect(form?.method).toBe('post');
    expect(form?.getAttribute('action')).toBe('/logout');
    expect(submit).toHaveBeenCalled();
  });

  it('carries the CSRF token the gateway requires for the logout POST', () => {
    document.cookie = 'XSRF-TOKEN=token-from-cookie';

    auth.logout();

    const token = document.querySelector<HTMLInputElement>('form input[name="_csrf"]');
    expect(token?.value).toBe('token-from-cookie');
  });

  it('still submits when no CSRF cookie has been issued yet', () => {
    auth.logout();

    expect(document.querySelector('form input[name="_csrf"]')).toBeNull();
    expect(submit).toHaveBeenCalled();
  });
});
