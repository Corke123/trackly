package org.unibl.etf.pisio.boardservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.unibl.etf.pisio.boardservice.exception.BoardNotFoundException;
import org.unibl.etf.pisio.boardservice.exception.IncompleteSwimlaneOrderException;
import org.unibl.etf.pisio.boardservice.exception.SwimlaneNotEmptyException;
import org.unibl.etf.pisio.boardservice.exception.SwimlaneNotOnBoardException;
import org.unibl.etf.pisio.boardservice.exception.TicketNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BoardNotFoundException.class)
    public ProblemDetail handleBoardNotFound(BoardNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ProblemDetail handleTicketNotFound(TicketNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(SwimlaneNotOnBoardException.class)
    public ProblemDetail handleSwimlaneNotOnBoard(SwimlaneNotOnBoardException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(SwimlaneNotEmptyException.class)
    public ProblemDetail handleSwimlaneNotEmpty(SwimlaneNotEmptyException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IncompleteSwimlaneOrderException.class)
    public ProblemDetail handleIncompleteSwimlaneOrder(IncompleteSwimlaneOrderException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }
}
