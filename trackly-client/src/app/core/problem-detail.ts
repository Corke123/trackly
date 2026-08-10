import { HttpErrorResponse } from '@angular/common/http';

/**
 * The services answer failures with RFC 9457 problem details, so the message the user sees is the
 * one the domain actually gave — "Swimlane 3 still holds 2 ticket(s)" rather than "409".
 */
export function describeError(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    const detail = (error.error as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string' && detail.length > 0) {
      return detail;
    }
    if (error.status === 403) {
      return 'You do not have permission to do that.';
    }
    if (error.status === 0) {
      return 'Trackly is unreachable. Check your connection and try again.';
    }
  }
  return fallback;
}
