package org.unibl.etf.pisio.boardservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import org.unibl.etf.pisio.boardservice.domain.event.TicketDeleted;
import org.unibl.etf.pisio.boardservice.domain.event.TicketMoved;
import org.unibl.etf.pisio.boardservice.exception.BoardNotFoundException;
import org.unibl.etf.pisio.boardservice.exception.IncompleteSwimlaneOrderException;
import org.unibl.etf.pisio.boardservice.exception.SwimlaneNotEmptyException;
import org.unibl.etf.pisio.boardservice.exception.SwimlaneNotOnBoardException;
import org.unibl.etf.pisio.boardservice.exception.TicketNotFoundException;
import org.unibl.etf.pisio.boardservice.outbox.DomainEventPublisher;
import org.unibl.etf.pisio.boardservice.repository.BoardRepository;
import org.unibl.etf.pisio.boardservice.repository.TicketRepository;

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
    @DisplayName(
            """
            Given a saved board that comes back with its swimlanes in another order, \
            when addSwimlane is called, \
            then the new swimlane is returned rather than the last one\
            """)
    void addSwimlaneFindsTheNewSwimlaneWhateverTheOrder() {
        Swimlane toDo = new Swimlane(10L, "To Do");
        Swimlane doing = new Swimlane(20L, "Doing");
        Swimlane done = new Swimlane(30L, "Done");
        Board existingBoard = new Board(1L, "Board", List.of(toDo, doing));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(existingBoard));
        when(boardRepository.save(any(Board.class))).thenReturn(new Board(1L, "Board", List.of(toDo, done, doing)));

        Swimlane result = boardService.addSwimlane(1L, "Done");

        assertThat(result).isEqualTo(done);
    }

    @Test
    @DisplayName(
            """
            Given an existing board, \
            when renameBoard is called, \
            then the renamed board is persisted and returned\
            """)
    void renameBoard() {
        Board existingBoard = new Board(1L, "Board", List.of(new Swimlane(10L, "To Do")));
        Board renamedBoard = new Board(1L, "Release board", List.of(new Swimlane(10L, "To Do")));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(existingBoard));
        when(boardRepository.save(renamedBoard)).thenReturn(renamedBoard);

        Board result = boardService.renameBoard(1L, "Release board");

        assertThat(result).isEqualTo(renamedBoard);
    }

    @Test
    @DisplayName(
            """
            Given a missing board, \
            when renameBoard is called, \
            then BoardNotFoundException is thrown and nothing is saved\
            """)
    void renameBoardMissingBoard() {
        when(boardRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BoardNotFoundException.class, () -> boardService.renameBoard(1L, "Release board"));

        verify(boardRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given a swimlane with no tickets, when deleteSwimlane is called, then the board is saved without it")
    void deleteSwimlane() {
        Board board = new Board(1L, "Board", List.of(new Swimlane(10L, "To Do"), new Swimlane(20L, "Done")));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(ticketRepository.countBySwimlaneId(10L)).thenReturn(0);

        boardService.deleteSwimlane(1L, 10L);

        verify(boardRepository).save(new Board(1L, "Board", List.of(new Swimlane(20L, "Done"))));
    }

    @Test
    @DisplayName(
            """
            Given a swimlane that still holds tickets, \
            when deleteSwimlane is called, \
            then SwimlaneNotEmptyException is thrown and the board is untouched\
            """)
    void deleteSwimlaneWithTickets() {
        Board board = new Board(1L, "Board", List.of(new Swimlane(10L, "To Do")));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(ticketRepository.countBySwimlaneId(10L)).thenReturn(2);

        assertThrows(SwimlaneNotEmptyException.class, () -> boardService.deleteSwimlane(1L, 10L));

        verify(boardRepository, never()).save(any());
    }

    @Test
    @DisplayName(
            """
            Given a swimlane that is not on the board, \
            when deleteSwimlane is called, \
            then SwimlaneNotOnBoardException is thrown\
            """)
    void deleteSwimlaneNotOnBoard() {
        Board board = new Board(1L, "Board", List.of(new Swimlane(10L, "To Do")));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(ticketRepository.countBySwimlaneId(99L)).thenReturn(0);

        assertThrows(SwimlaneNotOnBoardException.class, () -> boardService.deleteSwimlane(1L, 99L));

        verify(boardRepository, never()).save(any());
    }

    @Test
    @DisplayName(
            """
            Given every swimlane id in a new order, \
            when reorderSwimlanes is called, \
            then each swimlane is written to its new position\
            """)
    void reorderSwimlanes() {
        Swimlane toDo = new Swimlane(10L, "To Do");
        Swimlane doing = new Swimlane(20L, "Doing");
        Swimlane done = new Swimlane(30L, "Done");
        Board board = new Board(1L, "Board", List.of(toDo, doing, done));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

        Board result = boardService.reorderSwimlanes(1L, List.of(30L, 10L, 20L));

        assertThat(result.swimlanes()).containsExactly(done, toDo, doing);
        verify(boardRepository).updateSwimlaneOrder(1L, List.of(30L, 10L, 20L));
    }

    @Test
    @DisplayName(
            """
            Given an order that omits a swimlane, \
            when reorderSwimlanes is called, \
            then IncompleteSwimlaneOrderException is thrown and no position is written\
            """)
    void reorderSwimlanesIncomplete() {
        Board board = new Board(1L, "Board", List.of(new Swimlane(10L, "To Do"), new Swimlane(20L, "Doing")));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        List<Long> orderMissingToDo = List.of(20L);

        assertThrows(IncompleteSwimlaneOrderException.class, () -> boardService.reorderSwimlanes(1L, orderMissingToDo));

        verify(boardRepository, never()).updateSwimlaneOrder(any(), any());
    }

    @Test
    @DisplayName(
            """
            Given an order that names one swimlane twice, \
            when reorderSwimlanes is called, \
            then IncompleteSwimlaneOrderException is thrown and no position is written\
            """)
    void reorderSwimlanesDuplicateSwimlane() {
        Board board = new Board(1L, "Board",
                List.of(new Swimlane(10L, "To Do"), new Swimlane(20L, "Doing"), new Swimlane(30L, "Done")));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        List<Long> orderNamingDoingTwice = List.of(20L, 10L, 20L);

        assertThrows(IncompleteSwimlaneOrderException.class,
                () -> boardService.reorderSwimlanes(1L, orderNamingDoingTwice));

        verify(boardRepository, never()).updateSwimlaneOrder(any(), any());
    }

    @Test
    @DisplayName(
            """
            Given an order naming an unknown swimlane, \
            when reorderSwimlanes is called, \
            then IncompleteSwimlaneOrderException is thrown\
            """)
    void reorderSwimlanesUnknownSwimlane() {
        Board board = new Board(1L, "Board", List.of(new Swimlane(10L, "To Do"), new Swimlane(20L, "Doing")));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        List<Long> orderNamingSwimlane99 = List.of(10L, 99L);

        assertThrows(IncompleteSwimlaneOrderException.class,
                () -> boardService.reorderSwimlanes(1L, orderNamingSwimlane99));

        verify(boardRepository, never()).updateSwimlaneOrder(any(), any());
    }

    @Test
    @DisplayName("When listBoards is called, then every persisted board is returned")
    void listBoards() {
        List<Board> boards = List.of(new Board(1L, "Board", List.of()));
        when(boardRepository.findAll()).thenReturn(boards);

        assertThat(boardService.listBoards()).isEqualTo(boards);
    }

    @Test
    @DisplayName(
            """
            Given a missing board, \
            when addSwimlane is called, \
            then BoardNotFoundException is thrown and nothing is saved\
            """)
    void addSwimlaneMissingBoard() {
        when(boardRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BoardNotFoundException.class, () -> boardService.addSwimlane(1L, "To Do"));

        verify(boardRepository, never()).save(any());
    }

    @Test
    @DisplayName(
            """
            Given a board with a swimlane, \
            when createTicket is called, \
            then the ticket is persisted at the next position and a TicketCreated event is published\
            """)
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
    @DisplayName(
            """
            Given a missing board, \
            when createTicket is called, \
            then BoardNotFoundException is thrown and no ticket is created\
            """)
    void createTicketMissingBoard() {
        when(boardRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BoardNotFoundException.class, () -> boardService.createTicket(1L, 2L, "Title", "Desc", "actor-1"));

        verifyNoInteractions(ticketRepository, publisher);
    }

    @Test
    @DisplayName(
            """
            Given a swimlane that does not belong to the board, \
            when createTicket is called, \
            then SwimlaneNotOnBoardException is thrown and no ticket is created\
            """)
    void createTicketSwimlaneMissing() {
        Board board = new Board(1L, "Board", List.of());
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

        assertThrows(SwimlaneNotOnBoardException.class,
                () -> boardService.createTicket(1L, 2L, "Title", "Desc", "actor-1"));

        verifyNoInteractions(ticketRepository, publisher);
    }

    @Test
    @DisplayName(
            """
            Given a ticket moved into another swimlane, \
            when moveTicket is called, \
            then both swimlanes are renumbered densely and a TicketMoved event is published\
            """)
    void moveTicketAcrossSwimlanes() {

        Board board = new Board(1L, "Board", List.of(new Swimlane(10L, "To Do"), new Swimlane(20L, "Doing")));
        Ticket moved = new Ticket(100L, 1L, 10L, "Moved", "Desc", null, 1, null);
        Ticket sourceSibling = new Ticket(101L, 1L, 10L, "Stays behind", "Desc", null, 2, null);
        Ticket targetSibling = new Ticket(200L, 1L, 20L, "Already there", "Desc", null, 0, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(moved));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(ticketRepository.findBySwimlaneIdOrderByPositionAsc(10L)).thenReturn(List.of(moved, sourceSibling));
        when(ticketRepository.findBySwimlaneIdOrderByPositionAsc(20L)).thenReturn(List.of(targetSibling));
        when(ticketRepository.saveAll(anyList())).thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));

        Instant fixedNow = Instant.parse("2026-07-25T10:00:00Z");
        Ticket result;
        try (MockedStatic<Instant> instant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            instant.when(Instant::now).thenReturn(fixedNow);
            result = boardService.moveTicket(100L, 20L, 0, "actor-1");
        }

        // The gap the ticket left behind is closed, and the ticket lands ahead of the one already there.
        verify(ticketRepository).saveAll(List.of(sourceSibling.atPosition(0)));
        verify(ticketRepository).saveAll(List.of(
                new Ticket(100L, 1L, 20L, "Moved", "Desc", null, 0, null),
                targetSibling.atPosition(1)
        ));
        verify(publisher).publish(new TicketMoved(100L, 1L, 10L, 20L, "Moved", "Doing", null, "actor-1", fixedNow));
        assertThat(result).isEqualTo(new Ticket(100L, 1L, 20L, "Moved", "Desc", null, 0, null));
    }

    @Test
    @DisplayName(
            """
            Given a moved ticket that has an assignee, \
            when moveTicket is called, \
            then the event names the assignee and the swimlane it landed in\
            """)
    void moveTicketCarriesAssigneeAndSwimlaneTitle() {
        Board board = new Board(1L, "Board", List.of(new Swimlane(10L, "To Do"), new Swimlane(20L, "Doing")));
        Ticket ticket = new Ticket(100L, 1L, 10L, "Fix login", "Desc", "user-2", 0, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(ticketRepository.findBySwimlaneIdOrderByPositionAsc(10L)).thenReturn(List.of(ticket));
        when(ticketRepository.findBySwimlaneIdOrderByPositionAsc(20L)).thenReturn(List.of());
        when(ticketRepository.saveAll(anyList())).thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));

        Instant fixedNow = Instant.parse("2026-07-25T10:00:00Z");
        try (MockedStatic<Instant> instant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            instant.when(Instant::now).thenReturn(fixedNow);
            boardService.moveTicket(100L, 20L, 0, "actor-1");
        }

        verify(publisher)
                .publish(new TicketMoved(100L, 1L, 10L, 20L, "Fix login", "Doing", "user-2", "actor-1", fixedNow));
    }

    @Test
    @DisplayName(
            """
            Given a ticket reordered within its own swimlane, \
            when moveTicket is called, \
            then only that swimlane is renumbered\
            """)
    void moveTicketWithinSwimlane() {
        Board board = new Board(1L, "Board", List.of(new Swimlane(10L, "To Do")));
        Ticket first = new Ticket(100L, 1L, 10L, "First", "Desc", null, 0, null);
        Ticket second = new Ticket(101L, 1L, 10L, "Second", "Desc", null, 1, null);
        Ticket third = new Ticket(102L, 1L, 10L, "Third", "Desc", null, 2, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(first));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(ticketRepository.findBySwimlaneIdOrderByPositionAsc(10L)).thenReturn(List.of(first, second, third));
        when(ticketRepository.saveAll(anyList())).thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));

        Ticket result = boardService.moveTicket(100L, 10L, 2, "actor-1");

        verify(ticketRepository, times(1)).saveAll(anyList());
        verify(ticketRepository).saveAll(List.of(
                second.atPosition(0),
                third.atPosition(1),
                first.atPosition(2)
        ));
        assertThat(result.position()).isEqualTo(2);
    }

    @Test
    @DisplayName(
            """
            Given a ticket that keeps its position number but changes swimlane, \
            when moveTicket is called, \
            then it is still written\
            """)
    void moveTicketKeepingItsPositionNumber() {
        Board board = new Board(1L, "Board", List.of(new Swimlane(10L, "To Do"), new Swimlane(20L, "Doing")));
        Ticket ticket = new Ticket(100L, 1L, 10L, "Only", "Desc", null, 0, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(ticketRepository.findBySwimlaneIdOrderByPositionAsc(10L)).thenReturn(List.of(ticket));
        when(ticketRepository.findBySwimlaneIdOrderByPositionAsc(20L)).thenReturn(List.of());
        when(ticketRepository.saveAll(anyList())).thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));

        Ticket result = boardService.moveTicket(100L, 20L, 0, "actor-1");

        // Position 0 either side, but the swimlane is what moved — skipping to write would lose it.
        verify(ticketRepository).saveAll(List.of(new Ticket(100L, 1L, 20L, "Only", "Desc", null, 0, null)));
        assertThat(result.swimlaneId()).isEqualTo(20L);
    }

    @Test
    @DisplayName(
            """
            Given a ticket dropped back where it already was, \
            when moveTicket is called, \
            then only that ticket is written\
            """)
    void moveTicketOntoItsOwnPosition() {
        Board board = new Board(1L, "Board", List.of(new Swimlane(10L, "To Do")));
        Ticket first = new Ticket(100L, 1L, 10L, "First", "Desc", null, 0, null);
        Ticket second = new Ticket(101L, 1L, 10L, "Second", "Desc", null, 1, null);
        Ticket third = new Ticket(102L, 1L, 10L, "Third", "Desc", null, 2, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(first));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(ticketRepository.findBySwimlaneIdOrderByPositionAsc(10L)).thenReturn(List.of(first, second, third));
        when(ticketRepository.saveAll(anyList())).thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));

        boardService.moveTicket(100L, 10L, 0, "actor-1");

        // Nothing shifted, so the swimlane is not rewritten row by row.
        verify(ticketRepository).saveAll(List.of(first.atPosition(0)));
    }

    @Test
    @DisplayName(
            """
            Given a position past the end of the target swimlane, \
            when moveTicket is called, \
            then the ticket is appended rather than rejected\
            """)
    void moveTicketPastTheEnd() {
        Board board = new Board(1L, "Board", List.of(new Swimlane(10L, "To Do")));
        Ticket ticket = new Ticket(100L, 1L, 10L, "Only", "Desc", null, 0, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(ticketRepository.findBySwimlaneIdOrderByPositionAsc(10L)).thenReturn(List.of(ticket));
        when(ticketRepository.saveAll(anyList())).thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));

        Ticket result = boardService.moveTicket(100L, 10L, 99, "actor-1");

        assertThat(result.position()).isZero();
    }

    @Test
    @DisplayName(
            """
            Given a missing ticket, \
            when moveTicket is called, \
            then TicketNotFoundException is thrown and nothing is saved or published\
            """)
    void moveTicketMissingTicket() {
        when(ticketRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(TicketNotFoundException.class, () -> boardService.moveTicket(100L, 20L, 2, "actor-1"));

        verify(ticketRepository, never()).saveAll(anyList());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName(
            """
            Given a target swimlane that does not belong to the ticket's board, \
            when moveTicket is called, \
            then SwimlaneNotOnBoardException is thrown and nothing is saved or published\
            """)
    void moveTicketSwimlaneMissing() {
        Board board = new Board(1L, "Board", List.of());
        Ticket ticket = new Ticket(100L, 1L, 10L, "Title", "Desc", null, 0, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

        assertThrows(SwimlaneNotOnBoardException.class, () -> boardService.moveTicket(100L, 20L, 2, "actor-1"));

        verify(ticketRepository, never()).saveAll(anyList());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName(
            """
            Given an existing ticket, \
            when assignTicket is called, \
            then the ticket is assigned, persisted and a TicketAssigned event is published\
            """)
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
            verify(publisher).publish(new TicketAssigned(100L, 1L, "Title", "user-2", "actor-1", fixedNow));
            assertThat(result).isEqualTo(assignedTicket);
        }


    }

    @Test
    @DisplayName(
            """
            Given a missing ticket, \
            when assignTicket is called, \
            then TicketNotFoundException is thrown and nothing is saved or published\
            """)
    void assignTicketMissingTicket() {
        when(ticketRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(TicketNotFoundException.class, () -> boardService.assignTicket(100L, "user-2", "actor-1"));

        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName(
            """
            Given an existing ticket, \
            when deleteTicket is called, \
            then the ticket is removed and a TicketDeleted event is published\
            """)
    void deleteTicket() {
        Instant fixedNow = Instant.parse("2026-07-25T10:00:00Z");
        Board board = new Board(1L, "Board", List.of(new Swimlane(10L, "To Do")));
        Ticket ticket = new Ticket(100L, 1L, 10L, "Doomed", "Desc", "demo", 0, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

        try (MockedStatic<Instant> instant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            instant.when(Instant::now).thenReturn(fixedNow);
            boardService.deleteTicket(100L, "admin");
        }

        verify(ticketRepository).delete(ticket);
        verify(publisher).publish(new TicketDeleted(100L, 1L, 10L, "Doomed", "To Do", "demo", "admin", fixedNow));
    }

    @Test
    @DisplayName(
            """
            Given a ticket deleted from the middle of a swimlane, \
            when deleteTicket is called, \
            then the tickets behind it shift up to keep a dense order\
            """)
    void deleteTicketRenumbersRemainingTickets() {
        Board board = new Board(1L, "Board", List.of(new Swimlane(10L, "To Do")));
        Ticket first = new Ticket(100L, 1L, 10L, "First", null, null, 0, null);
        Ticket doomed = new Ticket(101L, 1L, 10L, "Doomed", null, null, 1, null);
        Ticket third = new Ticket(102L, 1L, 10L, "Third", null, null, 2, null);
        when(ticketRepository.findById(101L)).thenReturn(Optional.of(doomed));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(ticketRepository.findBySwimlaneIdOrderByPositionAsc(10L)).thenReturn(List.of(first, doomed, third));

        boardService.deleteTicket(101L, "admin");

        verify(ticketRepository).delete(doomed);
        verify(ticketRepository).saveAll(List.of(third.atPosition(1)));
    }

    @Test
    @DisplayName(
            """
            Given a missing ticket, \
            when deleteTicket is called, \
            then TicketNotFoundException is thrown and nothing is deleted or published\
            """)
    void deleteTicketMissingTicket() {
        when(ticketRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(TicketNotFoundException.class, () -> boardService.deleteTicket(404L, "admin"));

        verify(ticketRepository, never()).delete(any());
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
    @DisplayName(
            """
            Given an existing board with tickets, \
            when getBoardTickets is called, \
            then tickets ordered by swimlane and position are returned\
            """)
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
    @DisplayName(
            """
            Given a missing board id, \
            when getBoardTickets is called, \
            then BoardNotFoundException is thrown and tickets are never queried\
            """)
    void getBoardTicketsMissingBoard() {
        when(boardRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BoardNotFoundException.class, () -> boardService.getBoardTickets(1L));

        verify(ticketRepository, never()).findByBoardIdOrderBySwimlaneIdAscPositionAsc(any());
    }
}
