import { InjectionToken } from '@angular/core';

/**
 * Everything the SPA calls goes through the gateway on its own origin (ADR 0006), so the base is a
 * path rather than a host. It is a token so tests can point the client somewhere else.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => '/api',
});

/**
 * The gateway's OIDC logout. It is a full-page form POST rather than a fetch: logging out ends with
 * redirects to the authorization server and back, which only a navigation can follow.
 */
export const LOGOUT_URL = '/logout';
