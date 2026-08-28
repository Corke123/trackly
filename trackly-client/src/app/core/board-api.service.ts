import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { Board, BoardSummary, Comment, Swimlane, Ticket, User } from './board.models';

/**
 * The board-service and identity-service endpoints, reached through the gateway. Nothing here holds
 * state — that is the board store's job.
 */
@Injectable({ providedIn: 'root' })
export class BoardApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  listBoards(): Observable<BoardSummary[]> {
    return this.http.get<BoardSummary[]>(`${this.baseUrl}/boards`);
  }

  getBoard(boardId: number): Observable<Board> {
    return this.http.get<Board>(`${this.baseUrl}/boards/${boardId}`);
  }

  renameBoard(boardId: number, name: string): Observable<Board> {
    return this.http.patch<Board>(`${this.baseUrl}/boards/${boardId}`, { name });
  }

  addSwimlane(boardId: number, title: string): Observable<Swimlane> {
    return this.http.post<Swimlane>(`${this.baseUrl}/boards/${boardId}/swimlanes`, { title });
  }

  deleteSwimlane(boardId: number, swimlaneId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/boards/${boardId}/swimlanes/${swimlaneId}`);
  }

  reorderSwimlanes(boardId: number, swimlaneIds: readonly number[]): Observable<Board> {
    return this.http.put<Board>(`${this.baseUrl}/boards/${boardId}/swimlanes/order`, {
      swimlaneIds,
    });
  }

  createTicket(
    boardId: number,
    swimlaneId: number,
    title: string,
    description: string | null,
  ): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.baseUrl}/boards/${boardId}/tickets`, {
      swimlaneId,
      title,
      description,
    });
  }

  moveTicket(ticketId: number, swimlaneId: number, position: number): Observable<Ticket> {
    return this.http.patch<Ticket>(`${this.baseUrl}/tickets/${ticketId}`, { swimlaneId, position });
  }

  assignTicket(ticketId: number, assigneeId: string): Observable<Ticket> {
    return this.http.patch<Ticket>(`${this.baseUrl}/tickets/${ticketId}`, { assigneeId });
  }

  deleteTicket(ticketId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/tickets/${ticketId}`);
  }

  listComments(ticketId: number): Observable<Comment[]> {
    return this.http.get<Comment[]>(`${this.baseUrl}/tickets/${ticketId}/comments`);
  }

  postComment(ticketId: number, body: string): Observable<Comment> {
    return this.http.post<Comment>(`${this.baseUrl}/tickets/${ticketId}/comments`, { body });
  }

  deleteComment(ticketId: number, commentId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/tickets/${ticketId}/comments/${commentId}`);
  }

  listUsers(): Observable<User[]> {
    return this.http.get<User[]>(`${this.baseUrl}/users`);
  }
}
