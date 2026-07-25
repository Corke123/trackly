package org.unibl.etf.pisio.boardservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.unibl.etf.pisio.boardservice.exception.BoardNotFoundException;
import org.unibl.etf.pisio.boardservice.exception.SwimlaneNotOnBoardException;
import org.unibl.etf.pisio.boardservice.exception.TicketNotFoundException;

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
}
