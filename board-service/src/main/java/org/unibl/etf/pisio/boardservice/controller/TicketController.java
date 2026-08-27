package org.unibl.etf.pisio.boardservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide swimlaneId+position to move, and/or assigneeId to assign");
        }

        Ticket ticket = null;
        if (move) {
            if (request.swimlaneId() == null || request.position() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Both swimlaneId and position are required to move a ticket");
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

    @DeleteMapping("/{ticketId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long ticketId, @AuthenticationPrincipal Jwt jwt) {
        boardService.deleteTicket(ticketId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
