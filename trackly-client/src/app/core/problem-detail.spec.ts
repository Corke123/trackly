import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { describeError } from './problem-detail';

describe('describeError', () => {
  it('prefers the problem detail the service sent', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: { detail: 'Swimlane 10 still holds 2 ticket(s) and cannot be deleted' },
    });

    expect(describeError(error, 'fallback')).toBe(
      'Swimlane 10 still holds 2 ticket(s) and cannot be deleted',
    );
  });

  it('explains a refusal in its own words when the service sent no detail', () => {
    const error = new HttpErrorResponse({ status: 403 });

    expect(describeError(error, 'fallback')).toContain('permission');
  });

  it('names the real problem when the request never reached the gateway', () => {
    const error = new HttpErrorResponse({ status: 0 });

    expect(describeError(error, 'fallback')).toContain('unreachable');
  });

  it('falls back for a status it has nothing better to say about', () => {
    const error = new HttpErrorResponse({ status: 500 });

    expect(describeError(error, 'The board could not be loaded.')).toBe(
      'The board could not be loaded.',
    );
  });

  it('falls back for something that is not an HTTP failure at all', () => {
    expect(describeError(new Error('boom'), 'fallback')).toBe('fallback');
  });
});
