package org.unibl.etf.pisio.boardservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.unibl.etf.pisio.boardservice.exception.BoardNotFoundException;
import org.unibl.etf.pisio.boardservice.exception.IncompleteSwimlaneOrderException;
import org.unibl.etf.pisio.boardservice.exception.SwimlaneNotEmptyException;
import org.unibl.etf.pisio.boardservice.exception.SwimlaneNotOnBoardException;
import org.unibl.etf.pisio.boardservice.exception.TicketNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("Given a BoardNotFoundException, when handleBoardNotFound is called, then a 404 problem detail is returned")
    void handleBoardNotFound() {
        BoardNotFoundException exception = new BoardNotFoundException(1L);

        ProblemDetail result = handler.handleBoardNotFound(exception);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.getDetail()).isEqualTo("Board 1 not found");
    }

    @Test
    @DisplayName("Given a TicketNotFoundException, when handleTicketNotFound is called, then a 404 problem detail is returned")
    void handleTicketNotFound() {
        TicketNotFoundException exception = new TicketNotFoundException(100L);

        ProblemDetail result = handler.handleTicketNotFound(exception);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.getDetail()).isEqualTo("Ticket 100 not found");
    }

    @Test
    @DisplayName("Given a SwimlaneNotOnBoardException, when handleSwimlaneNotOnBoard is called, then a 422 problem detail is returned")
    void handleSwimlaneNotOnBoard() {
        SwimlaneNotOnBoardException exception = new SwimlaneNotOnBoardException(1L, 20L);

        ProblemDetail result = handler.handleSwimlaneNotOnBoard(exception);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
        assertThat(result.getDetail()).isEqualTo("Swimlane 20 not on the board 1");
    }

    @Test
    @DisplayName("Given a SwimlaneNotEmptyException, when handleSwimlaneNotEmpty is called, then a 409 problem detail is returned")
    void handleSwimlaneNotEmpty() {
        SwimlaneNotEmptyException exception = new SwimlaneNotEmptyException(20L, 3);

        ProblemDetail result = handler.handleSwimlaneNotEmpty(exception);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(result.getDetail()).isEqualTo("Swimlane 20 still holds 3 ticket(s) and cannot be deleted");
    }

    @Test
    @DisplayName("Given an IncompleteSwimlaneOrderException, when handleIncompleteSwimlaneOrder is called, then a 422 problem detail is returned")
    void handleIncompleteSwimlaneOrder() {
        IncompleteSwimlaneOrderException exception =
                new IncompleteSwimlaneOrderException(1L, List.of(10L, 20L), List.of(10L));

        ProblemDetail result = handler.handleIncompleteSwimlaneOrder(exception);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
        assertThat(result.getDetail()).contains("must list every swimlane exactly once");
    }
}
