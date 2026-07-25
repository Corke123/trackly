package org.unibl.etf.pisio.boardservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

import java.net.URI;

@RestController
@RequestMapping("/boards")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @PostMapping
    public ResponseEntity<BoardView> createBoard(@Valid @RequestBody CreateBoard request) {
        Board board = boardService.createBoard(request.name());
        return ResponseEntity.created(URI.create("/boards/" + board.id()))
                .body(BoardView.of(board, boardService.getBoardTickets(board.id())));
    }

    @GetMapping("/{boardId}")
    public BoardView getBoard(@PathVariable Long boardId) {
        Board board = boardService.getBoard(boardId);
        return BoardView.of(board, boardService.getBoardTickets(boardId));
    }

    @PostMapping("/{boardId}/swimlanes")
    public ResponseEntity<SwimlaneView> addSwimlane(@PathVariable Long boardId, @Valid @RequestBody CreateSwimlane request) {
        Swimlane created = boardService.addSwimlane(boardId, request.title());
        return ResponseEntity.created(URI.create("/boards/" + boardId))
                .body(SwimlaneView.of(created));
    }

    @PostMapping("/{boardId}/tickets")
    public ResponseEntity<TicketView> createTicket(@PathVariable Long boardId, @Valid @RequestBody CreateTicket request) {
        Ticket ticket = boardService.createTicket(boardId, request.swimlaneId(), request.title(), request.description(), "anonymous");
        return ResponseEntity.created(URI.create("/tickets/" + ticket.id()))
                .body(TicketView.of(ticket));
    }
}
