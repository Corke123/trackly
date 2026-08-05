import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

/** Where the gateway starts an OAuth2 login for the single `trackly` client registration. */
export const LOGIN_URL = '/oauth2/authorization/trackly';

/**
 * The gateway answers an expired session on `/api/**` with a 401 rather than a redirect, so the SPA
 * decides what to do with it: leave the app and log in again, coming back to a working session.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const document = inject(DOCUMENT);

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        const window = document.defaultView;
        if (window) {
          window.location.assign(LOGIN_URL);
        }
      }
      return throwError(() => error);
    }),
  );
};
