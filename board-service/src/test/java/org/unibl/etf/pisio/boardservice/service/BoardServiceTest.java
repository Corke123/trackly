package org.unibl.etf.pisio.boardservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unibl.etf.pisio.boardservice.domain.Board;
import org.unibl.etf.pisio.boardservice.domain.Swimlane;
import org.unibl.etf.pisio.boardservice.domain.Ticket;
import org.unibl.etf.pisio.boardservice.domain.event.TicketAssigned;
import org.unibl.etf.pisio.boardservice.domain.event.TicketCreated;
import org.unibl.etf.pisio.boardservice.domain.event.TicketMoved;
import org.unibl.etf.pisio.boardservice.exception.BoardNotFoundException;
import org.unibl.etf.pisio.boardservice.exception.SwimlaneNotOnBoardException;
import org.unibl.etf.pisio.boardservice.exception.TicketNotFoundException;
import org.unibl.etf.pisio.boardservice.outbox.DomainEventPublisher;
import org.unibl.etf.pisio.boardservice.repository.BoardRepository;
import org.unibl.etf.pisio.boardservice.repository.TicketRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private DomainEventPublisher publisher;

    @InjectMocks
    private BoardService boardService;

    @Test
    @DisplayName("Given a board name, when createBoard is called, then a new board is persisted and returned")
    void createBoard() {
        Board savedBoard = new Board(1L, "Sprint board", List.of());
        when(boardRepository.save(any(Board.class))).thenReturn(savedBoard);

        Board result = boardService.createBoard("Sprint board");

        assertThat(result).isEqualTo(savedBoard);
    }

    @Test
    @DisplayName("Given an existing board, when addSwimlane is called, then the swimlane is appended and persisted")
    void addSwimlane() {
        Board existingBoard = new Board(1L, "Board", List.of());
        Swimlane persistedSwimlane = new Swimlane(10L, "To Do");
        Board savedBoard = new Board(1L, "Board", List.of(persistedSwimlane));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(existingBoard));
        when(boardRepository.save(any(Board.class))).thenReturn(savedBoard);

        Swimlane result = boardService.addSwimlane(1L, "To Do");

        verify(boardRepository).save(new Board(1L, "Board", List.of(new Swimlane(null, "To Do"))));
        assertThat(result).isEqualTo(persistedSwimlane);
    }

    @Test
    @DisplayName("Given a missing board, when addSwimlane is called, then BoardNotFoundException is thrown and nothing is saved")
    void addSwimlaneMissingBoard() {
        when(boardRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BoardNotFoundException.class, () -> boardService.addSwimlane(1L, "To Do"));

        verify(boardRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given a board with a swimlane, when createTicket is called, then the ticket is persisted at the next position and a TicketCreated event is published")
    void createTicket() {
        Instant fixedNow = Instant.parse("2026-07-25T10:00:00Z");
        Swimlane swimlane = new Swimlane(2L, "To Do");
        Board board = new Board(1L, "Board", List.of(swimlane));
        Ticket savedTicket = new Ticket(100L, 1L, 2L, "Title", "Desc", null, 3, fixedNow);
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(ticketRepository.countBySwimlaneId(2L)).thenReturn(3);
        when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);

        Ticket result;
        try (MockedStatic<Instant> instant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            instant.when(Instant::now).thenReturn(fixedNow);
            result = boardService.createTicket(1L, 2L, "Title", "Desc", "actor-1");
        }

        verify(ticketRepository).save(new Ticket(null, 1L, 2L, "Title", "Desc", null, 3, fixedNow));
        verify(publisher).publish(new TicketCreated(100L, 1L, 2L, "Title", "actor-1", fixedNow));
        assertThat(result).isEqualTo(savedTicket);
    }

    @Test
    @DisplayName("Given a missing board, when createTicket is called, then BoardNotFoundException is thrown and no ticket is created")
    void createTicketMissingBoard() {
        when(boardRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BoardNotFoundException.class, () -> boardService.createTicket(1L, 2L, "Title", "Desc", "actor-1"));

        verifyNoInteractions(ticketRepository, publisher);
    }

    @Test
    @DisplayName("Given a swimlane that does not belong to the board, when createTicket is called, then SwimlaneNotOnBoardException is thrown and no ticket is created")
    void createTicketSwimlaneMissing() {
        Board board = new Board(1L, "Board", List.of());
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

        assertThrows(SwimlaneNotOnBoardException.class, () -> boardService.createTicket(1L, 2L, "Title", "Desc", "actor-1"));

        verifyNoInteractions(ticketRepository, publisher);
    }

    @Test
    @DisplayName("Given an existing ticket and a valid target swimlane, when moveTicket is called, then the ticket is relocated and a TicketMoved event is published")
    void moveTicket() {
        Instant fixedNow = Instant.parse("2026-07-25T10:00:00Z");
        Swimlane targetSwimlane = new Swimlane(20L, "Doing");
        Board board = new Board(1L, "Board", List.of(targetSwimlane));
        Ticket ticket = new Ticket(100L, 1L, 10L, "Title", "Desc", null, 0, null);
        Ticket movedTicket = new Ticket(100L, 1L, 20L, "Title", "Desc", null, 2, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(movedTicket);

        Ticket result;
        try (MockedStatic<Instant> instant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            instant.when(Instant::now).thenReturn(fixedNow);
            result = boardService.moveTicket(100L, 20L, 2, "actor-1");
        }

        verify(ticketRepository).save(new Ticket(100L, 1L, 20L, "Title", "Desc", null, 2, null));
        verify(publisher).publish(new TicketMoved(100L, 1L, 10L, 20L, "actor-1", fixedNow));
        assertThat(result).isEqualTo(movedTicket);
    }

    @Test
    @DisplayName("Given a missing ticket, when moveTicket is called, then TicketNotFoundException is thrown and nothing is saved or published")
    void moveTicketMissingTicket() {
        when(ticketRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(TicketNotFoundException.class, () -> boardService.moveTicket(100L, 20L, 2, "actor-1"));

        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("Given a target swimlane that does not belong to the ticket's board, when moveTicket is called, then SwimlaneNotOnBoardException is thrown and nothing is saved or published")
    void moveTicketSwimlaneMissing() {
        Board board = new Board(1L, "Board", List.of());
        Ticket ticket = new Ticket(100L, 1L, 10L, "Title", "Desc", null, 0, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

        assertThrows(SwimlaneNotOnBoardException.class, () -> boardService.moveTicket(100L, 20L, 2, "actor-1"));

        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("Given an existing ticket, when assignTicket is called, then the ticket is assigned, persisted and a TicketAssigned event is published")
    void assignTicket() {
        Instant fixedNow = Instant.parse("2026-07-25T10:00:00Z");
        Ticket ticket = new Ticket(100L, 1L, 10L, "Title", "Desc", null, 0, null);
        Ticket assignedTicket = new Ticket(100L, 1L, 10L, "Title", "Desc", "user-2", 0, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(assignedTicket);

        try (MockedStatic<Instant> instant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            instant.when(Instant::now).thenReturn(fixedNow);
            Ticket result = boardService.assignTicket(100L, "user-2", "actor-1");

            verify(ticketRepository).save(new Ticket(100L, 1L, 10L, "Title", "Desc", "user-2", 0, null));
            verify(publisher).publish(new TicketAssigned(100L, 1L, "user-2", "actor-1", fixedNow));
            assertThat(result).isEqualTo(assignedTicket);
        }


    }

    @Test
    @DisplayName("Given a missing ticket, when assignTicket is called, then TicketNotFoundException is thrown and nothing is saved or published")
    void assignTicketMissingTicket() {
        when(ticketRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(TicketNotFoundException.class, () -> boardService.assignTicket(100L, "user-2", "actor-1"));

        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("Given an existing board id, when getBoard is called, then the matching board is returned")
    void getBoard() {
        Board board = new Board(1L, "Board", List.of());
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

        Board result = boardService.getBoard(1L);

        assertThat(result).isEqualTo(board);
    }

    @Test
    @DisplayName("Given a missing board id, when getBoard is called, then BoardNotFoundException is thrown")
    void getBoardMissing() {
        when(boardRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BoardNotFoundException.class, () -> boardService.getBoard(1L));
    }

    @Test
    @DisplayName("Given an existing ticket id, when getTicket is called, then the matching ticket is returned")
    void getTicket() {
        Ticket ticket = new Ticket(100L, 1L, 10L, "Title", "Desc", null, 0, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

        Ticket result = boardService.getTicket(100L);

        assertThat(result).isEqualTo(ticket);
    }

    @Test
    @DisplayName("Given a missing ticket id, when getTicket is called, then TicketNotFoundException is thrown")
    void getTicketMissing() {
        when(ticketRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(TicketNotFoundException.class, () -> boardService.getTicket(100L));
    }

    @Test
    @DisplayName("Given an existing board with tickets, when getBoardTickets is called, then tickets ordered by swimlane and position are returned")
    void getBoardTickets() {
        Board board = new Board(1L, "Board", List.of());
        List<Ticket> tickets = List.of(
                new Ticket(100L, 1L, 10L, "Title 1", "Desc", null, 0, null),
                new Ticket(101L, 1L, 10L, "Title 2", "Desc", null, 1, null)
        );
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(ticketRepository.findByBoardIdOrderBySwimlaneIdAscPositionAsc(1L)).thenReturn(tickets);

        List<Ticket> result = boardService.getBoardTickets(1L);

        assertThat(result).isEqualTo(tickets);
        verify(boardRepository).findById(1L);
        verify(ticketRepository).findByBoardIdOrderBySwimlaneIdAscPositionAsc(1L);
    }

    @Test
    @DisplayName("Given a missing board id, when getBoardTickets is called, then BoardNotFoundException is thrown and tickets are never queried")
    void getBoardTicketsMissingBoard() {
        when(boardRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BoardNotFoundException.class, () -> boardService.getBoardTickets(1L));

        verify(ticketRepository, never()).findByBoardIdOrderBySwimlaneIdAscPositionAsc(any());
    }
}
