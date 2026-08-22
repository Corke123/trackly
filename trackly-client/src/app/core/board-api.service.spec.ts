import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { aBoard } from '../../testing/board.fixtures';
import { BoardApiService } from './board-api.service';

describe('BoardApiService', () => {
  let api: BoardApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), BoardApiService],
    });
    api = TestBed.inject(BoardApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists boards under the gateway api prefix', () => {
    api.listBoards().subscribe();

    http.expectOne({ method: 'GET', url: '/api/boards' }).flush([]);
  });

  it('fetches one board', () => {
    api.getBoard(1).subscribe();

    http.expectOne({ method: 'GET', url: '/api/boards/1' }).flush(aBoard());
  });

  it('renames a board with a PATCH, the way board-service expects', () => {
    api.renameBoard(1, 'Release board').subscribe();

    const request = http.expectOne({ method: 'PATCH', url: '/api/boards/1' });
    expect(request.request.body).toEqual({ name: 'Release board' });
    request.flush(aBoard());
  });

  it('adds a swimlane', () => {
    api.addSwimlane(1, 'Blocked').subscribe();

    const request = http.expectOne({ method: 'POST', url: '/api/boards/1/swimlanes' });
    expect(request.request.body).toEqual({ title: 'Blocked' });
    request.flush({ id: 40, title: 'Blocked', tickets: [] });
  });

  it('deletes a swimlane', () => {
    api.deleteSwimlane(1, 10).subscribe();

    http.expectOne({ method: 'DELETE', url: '/api/boards/1/swimlanes/10' }).flush(null);
  });

  it('deletes a ticket', () => {
    api.deleteTicket(100).subscribe();

    http.expectOne({ method: 'DELETE', url: '/api/tickets/100' }).flush(null);
  });

  it('sends the complete swimlane order when reordering', () => {
    api.reorderSwimlanes(1, [30, 10, 20]).subscribe();

    const request = http.expectOne({ method: 'PUT', url: '/api/boards/1/swimlanes/order' });
    expect(request.request.body).toEqual({ swimlaneIds: [30, 10, 20] });
    request.flush(aBoard());
  });

  it('creates a ticket in a swimlane', () => {
    api.createTicket(1, 10, 'Write tests', 'Details').subscribe();

    const request = http.expectOne({ method: 'POST', url: '/api/boards/1/tickets' });
    expect(request.request.body).toEqual({
      swimlaneId: 10,
      title: 'Write tests',
      description: 'Details',
    });
    request.flush({});
  });

  it('moves a ticket with the swimlane and position board-service requires together', () => {
    api.moveTicket(100, 20, 2).subscribe();

    const request = http.expectOne({ method: 'PATCH', url: '/api/tickets/100' });
    expect(request.request.body).toEqual({ swimlaneId: 20, position: 2 });
    request.flush({});
  });

  it('assigns a ticket without touching where it sits', () => {
    api.assignTicket(100, 'demo').subscribe();

    const request = http.expectOne({ method: 'PATCH', url: '/api/tickets/100' });
    expect(request.request.body).toEqual({ assigneeId: 'demo' });
    request.flush({});
  });

  it('lists the users identity-service knows about', () => {
    api.listUsers().subscribe();

    http.expectOne({ method: 'GET', url: '/api/users' }).flush([]);
  });
});
