package org.unibl.etf.pisio.boardservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView.TicketView;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.UpdateTicket;
import org.unibl.etf.pisio.boardservice.domain.Ticket;
import org.unibl.etf.pisio.boardservice.service.BoardService;

@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    @Mock
    private BoardService boardService;

    @InjectMocks
    private TicketController ticketController;

    @Test
    @DisplayName("Given an existing ticket id, when getTicket is called, then the ticket view is returned")
    void getTicket() {
        Ticket ticket = new Ticket(100L, 1L, 10L, "Title", "Desc", null, 0, Instant.parse("2026-07-25T10:00:00Z"));
        when(boardService.getTicket(100L)).thenReturn(ticket);

        TicketView result = ticketController.getTicket(100L);

        assertThat(result).isEqualTo(TicketView.of(ticket));
    }

    @Test
    @DisplayName("Given swimlaneId and position, when updateTicket is called, then the ticket is moved")
    void updateTicketMove() {
        Ticket moved = new Ticket(100L, 1L, 20L, "Title", "Desc", null, 2, Instant.parse("2026-07-25T10:00:00Z"));
        when(boardService.moveTicket(100L, 20L, 2, "demo")).thenReturn(moved);

        TicketView result = ticketController.updateTicket(100L, new UpdateTicket(20L, 2, null), jwtForDemoUser());

        assertThat(result).isEqualTo(TicketView.of(moved));
    }

    @Test
    @DisplayName("Given an assigneeId, when updateTicket is called, then the ticket is assigned")
    void updateTicketAssign() {
        Ticket assigned =
                new Ticket(100L, 1L, 10L, "Title", "Desc", "user-1", 0, Instant.parse("2026-07-25T10:00:00Z"));
        when(boardService.assignTicket(100L, "user-1", "demo")).thenReturn(assigned);

        TicketView result =
                ticketController.updateTicket(100L, new UpdateTicket(null, null, "user-1"), jwtForDemoUser());

        assertThat(result).isEqualTo(TicketView.of(assigned));
    }

    @Test
    @DisplayName(
            """
            Given swimlaneId, position and assigneeId, \
            when updateTicket is called, \
            then the ticket is moved and assigned\
            """)
    void updateTicketMoveAndAssign() {
        Ticket moved = new Ticket(100L, 1L, 20L, "Title", "Desc", null, 2, Instant.parse("2026-07-25T10:00:00Z"));
        Ticket assigned =
                new Ticket(100L, 1L, 20L, "Title", "Desc", "user-1", 2, Instant.parse("2026-07-25T10:00:00Z"));
        when(boardService.moveTicket(100L, 20L, 2, "demo")).thenReturn(moved);
        when(boardService.assignTicket(100L, "user-1", "demo")).thenReturn(assigned);

        TicketView result = ticketController.updateTicket(100L, new UpdateTicket(20L, 2, "user-1"), jwtForDemoUser());

        assertThat(result).isEqualTo(TicketView.of(assigned));
    }

    @Test
    @DisplayName("Given neither move nor assign fields, when updateTicket is called, then a 400 error is thrown")
    void updateTicketWithoutMoveOrAssign() {
        UpdateTicket request = new UpdateTicket(null, null, null);
        var jwt = jwtForDemoUser();

        assertThatThrownBy(() -> ticketController.updateTicket(100L, request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Provide swimlaneId+position to move, and/or assigneeId to assign");

        verifyNoInteractions(boardService);
    }

    @Test
    @DisplayName("Given only swimlaneId without position, when updateTicket is called, then a 400 error is thrown")
    void updateTicketWithSwimlaneIdOnly() {
        UpdateTicket request = new UpdateTicket(20L, null, null);
        var jwt = jwtForDemoUser();

        assertThatThrownBy(() -> ticketController.updateTicket(100L, request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Both swimlaneId and position are required to move a ticket");

        verifyNoInteractions(boardService);
    }

    @Test
    @DisplayName("Given only position without swimlaneId, when updateTicket is called, then a 400 error is thrown")
    void updateTicketWithPositionOnly() {
        UpdateTicket request = new UpdateTicket(null, 2, null);
        var jwt = jwtForDemoUser();

        assertThatThrownBy(() -> ticketController.updateTicket(100L, request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Both swimlaneId and position are required to move a ticket");

        verifyNoInteractions(boardService);
    }

    @Test
    @DisplayName("Given a blank assigneeId, when updateTicket is called, then a 400 error is thrown")
    void updateTicketWithBlankAssigneeId() {
        UpdateTicket request = new UpdateTicket(null, null, " ");
        var jwt = jwtForDemoUser();

        assertThatThrownBy(() -> ticketController.updateTicket(100L, request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("assigneeId must not be blank");

        verifyNoInteractions(boardService);
    }

    @Test
    @DisplayName(
            """
            Given a ticket id, \
            when deleteTicket is called, \
            then the service is asked to delete it and no content is returned\
            """)
    void deleteTicket() {
        ResponseEntity<Void> result = ticketController.deleteTicket(100L, jwtForDemoUser());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(boardService).deleteTicket(100L, "demo");
    }

    private static Jwt jwtForDemoUser() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("demo")
                .claim("roles", List.of("ROLE_USER"))
                .build();
    }
}
