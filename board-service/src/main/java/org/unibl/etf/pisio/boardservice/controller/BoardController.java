package org.unibl.etf.pisio.boardservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardSummary;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView.SwimlaneView;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView.TicketView;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.CreateBoard;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.CreateSwimlane;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.CreateTicket;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.RenameBoard;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.ReorderSwimlanes;
import org.unibl.etf.pisio.boardservice.domain.Board;
import org.unibl.etf.pisio.boardservice.domain.Swimlane;
import org.unibl.etf.pisio.boardservice.domain.Ticket;
import org.unibl.etf.pisio.boardservice.service.BoardService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/boards")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping
    public List<BoardSummary> listBoards() {
        return boardService.listBoards().stream()
                .map(BoardSummary::of)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
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

    @PatchMapping("/{boardId}")
    @PreAuthorize("hasRole('ADMIN')")
    public BoardView renameBoard(@PathVariable Long boardId, @Valid @RequestBody RenameBoard request) {
        Board board = boardService.renameBoard(boardId, request.name());
        return BoardView.of(board, boardService.getBoardTickets(boardId));
    }

    @PostMapping("/{boardId}/swimlanes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SwimlaneView> addSwimlane(@PathVariable Long boardId, @Valid @RequestBody CreateSwimlane request) {
        Swimlane created = boardService.addSwimlane(boardId, request.title());
        return ResponseEntity.created(URI.create("/boards/" + boardId))
                .body(SwimlaneView.of(created));
    }

    @DeleteMapping("/{boardId}/swimlanes/{swimlaneId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSwimlane(@PathVariable Long boardId, @PathVariable Long swimlaneId) {
        boardService.deleteSwimlane(boardId, swimlaneId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{boardId}/swimlanes/order")
    @PreAuthorize("hasRole('ADMIN')")
    public BoardView reorderSwimlanes(@PathVariable Long boardId, @Valid @RequestBody ReorderSwimlanes request) {
        Board board = boardService.reorderSwimlanes(boardId, request.swimlaneIds());
        return BoardView.of(board, boardService.getBoardTickets(boardId));
    }

    @PostMapping("/{boardId}/tickets")
    public ResponseEntity<TicketView> createTicket(@PathVariable Long boardId,
                                                   @Valid @RequestBody CreateTicket request,
                                                   @AuthenticationPrincipal Jwt actor) {
        Ticket ticket = boardService.createTicket(boardId, request.swimlaneId(), request.title(), request.description(), actor.getSubject());
        return ResponseEntity.created(URI.create("/tickets/" + ticket.id()))
                .body(TicketView.of(ticket));
    }
}
