package org.unibl.etf.pisio.boardservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView.SwimlaneView;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView.TicketView;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.CreateBoard;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.CreateSwimlane;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.CreateTicket;
import org.unibl.etf.pisio.boardservice.domain.Board;
import org.unibl.etf.pisio.boardservice.domain.Swimlane;
import org.unibl.etf.pisio.boardservice.domain.Ticket;
import org.unibl.etf.pisio.boardservice.service.BoardService;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardControllerTest {

    @Mock
    private BoardService boardService;

    @InjectMocks
    private BoardController boardController;

    @Test
    @DisplayName("Given a valid request, when createBoard is called, then a 201 response with the created board is returned")
    void createBoard() {
        Board board = new Board(1L, "Sprint board", List.of());
        when(boardService.createBoard("Sprint board")).thenReturn(board);
        when(boardService.getBoardTickets(1L)).thenReturn(List.of());

        ResponseEntity<BoardView> response = boardController.createBoard(new CreateBoard("Sprint board"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/boards/1");
        assertThat(response.getBody()).isEqualTo(new BoardView(1L, "Sprint board", List.of()));
    }

    @Test
    @DisplayName("Given an existing board id, when getBoard is called, then the board view with its tickets is returned")
    void getBoard() {
        Swimlane swimlane = new Swimlane(10L, "To Do");
        Board board = new Board(1L, "Board", List.of(swimlane));
        Ticket ticket = new Ticket(100L, 1L, 10L, "Title", "Desc", null, 0, Instant.parse("2026-07-25T10:00:00Z"));
        when(boardService.getBoard(1L)).thenReturn(board);
        when(boardService.getBoardTickets(1L)).thenReturn(List.of(ticket));

        BoardView result = boardController.getBoard(1L);

        assertThat(result).isEqualTo(new BoardView(1L, "Board", List.of(
                new SwimlaneView(10L, "To Do", List.of(new TicketView(100L, "Title", "Desc", null, 0)))
        )));
    }

    @Test
    @DisplayName("Given a valid request, when addSwimlane is called, then a 201 response with the created swimlane is returned")
    void addSwimlane() {
        Swimlane created = new Swimlane(10L, "To Do");
        when(boardService.addSwimlane(1L, "To Do")).thenReturn(created);

        ResponseEntity<SwimlaneView> response = boardController.addSwimlane(1L, new CreateSwimlane("To Do"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/boards/1");
        assertThat(response.getBody()).isEqualTo(SwimlaneView.of(created));
    }

    @Test
    @DisplayName("Given a valid request, when createTicket is called, then a 201 response with the created ticket is returned")
    void createTicket() {
        Ticket ticket = new Ticket(100L, 1L, 10L, "Title", "Desc", null, 0, Instant.parse("2026-07-25T10:00:00Z"));
        when(boardService.createTicket(1L, 10L, "Title", "Desc", "anonymous")).thenReturn(ticket);

        ResponseEntity<TicketView> response = boardController.createTicket(1L, new CreateTicket(10L, "Title", "Desc"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/tickets/100");
        assertThat(response.getBody()).isEqualTo(TicketView.of(ticket));
    }
}
