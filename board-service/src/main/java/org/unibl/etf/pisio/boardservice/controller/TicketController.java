package org.unibl.etf.pisio.boardservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView.TicketView;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.UpdateTicket;
import org.unibl.etf.pisio.boardservice.domain.Ticket;
import org.unibl.etf.pisio.boardservice.service.BoardService;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final BoardService boardService;

    public TicketController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping("/{ticketId}")
    public TicketView getTicket(@PathVariable Long ticketId) {
        return TicketView.of(boardService.getTicket(ticketId));
    }

    @PatchMapping("/{ticketId}")
    public TicketView updateTicket(@PathVariable Long ticketId,
                                   @Valid @RequestBody UpdateTicket request,
                                   @AuthenticationPrincipal Jwt jwt) {
        String actor = jwt.getSubject();
        boolean move = request.swimlaneId() != null || request.position() != null;
        boolean assign = request.assigneeId() != null;

        if (!move && !assign) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide swimlaneId+position to move, and/or assigneeId to assign");
        }

        Ticket ticket = null;
        if (move) {
            if (request.swimlaneId() == null || request.position() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both swimlaneId and position are required to move a ticket");
            }
            ticket = boardService.moveTicket(ticketId, request.swimlaneId(), request.position(), actor);
        }
        if (assign) {
            if (request.assigneeId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assigneeId must not be blank");
            }
            ticket = boardService.assignTicket(ticketId, request.assigneeId(), actor);
        }

        return TicketView.of(ticket);
    }
}
