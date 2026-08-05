import { DOCUMENT } from '@angular/common';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LOGIN_URL, authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  const assign = vi.fn();
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    assign.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        {
          provide: DOCUMENT,
          useValue: { defaultView: { location: { assign } } },
        },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  it('sends the browser to log in again when the session has expired', () => {
    http.get('/api/boards').subscribe({ error: () => undefined });

    backend.expectOne('/api/boards').flush('', { status: 401, statusText: 'Unauthorized' });

    expect(assign).toHaveBeenCalledWith(LOGIN_URL);
  });

  it('leaves other failures for the caller to explain', () => {
    let status = 0;
    http.get('/api/boards/1').subscribe({ error: (error) => (status = error.status) });

    backend.expectOne('/api/boards/1').flush('', { status: 404, statusText: 'Not Found' });

    expect(assign).not.toHaveBeenCalled();
    expect(status).toBe(404);
  });

  it('passes successful responses straight through', () => {
    let body: unknown;
    http.get('/api/me').subscribe((response) => (body = response));

    backend.expectOne('/api/me').flush({ username: 'demo' });

    expect(body).toEqual({ username: 'demo' });
    expect(assign).not.toHaveBeenCalled();
  });
});
