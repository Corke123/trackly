package org.unibl.etf.pisio.boardservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

@Service
@Transactional
public class BoardService {

    private final BoardRepository boardRepository;
    private final TicketRepository ticketRepository;
    private final DomainEventPublisher publisher;


    public BoardService(BoardRepository boardRepository, TicketRepository ticketRepository, DomainEventPublisher publisher) {
        this.boardRepository = boardRepository;
        this.ticketRepository = ticketRepository;
        this.publisher = publisher;
    }

    public Board createBoard(String name) {
        return boardRepository.save(new Board(name));
    }

    public Swimlane addSwimlane(Long boardId, String title) {
        Board board = requireBoard(boardId);

        Board boardWithNewSwimlane = board.addSwimlane(title);
        Board updatedBoard = boardRepository.save(boardWithNewSwimlane);

        List<Swimlane> swimlanes = updatedBoard.swimlanes();
        return swimlanes.getLast();
    }

    public Ticket createTicket(Long boardId, Long swimlaneId, String title, String description, String actorId) {
        Board board = requireBoard(boardId);

        if (!board.hasSwimlane(swimlaneId)) {
            throw new SwimlaneNotOnBoardException(boardId, swimlaneId);
        }

        int position = ticketRepository.countBySwimlaneId(swimlaneId);
        Ticket ticket = ticketRepository.save(new Ticket(boardId, swimlaneId, title, description, position));

        publisher.publish(new TicketCreated(ticket.id(), boardId, swimlaneId, title, actorId, Instant.now()));
        return ticket;
    }

    public Ticket moveTicket(Long ticketId, Long toSwimlaneId, int toPosition, String actorId) {
        Ticket ticket = requireTicket(ticketId);
        Board board = requireBoard(ticket.boardId());

        if (!board.hasSwimlane(toSwimlaneId)) {
            throw new SwimlaneNotOnBoardException(ticket.boardId(), toSwimlaneId);
        }

        Long from = ticket.swimlaneId();

        Ticket movedTicket = ticket.moveTo(toSwimlaneId, toPosition);
        movedTicket = ticketRepository.save(movedTicket);

        publisher.publish(new TicketMoved(ticketId, movedTicket.boardId(), from, toSwimlaneId, actorId, Instant.now()));
        return movedTicket;
    }

    public Ticket assignTicket(Long ticketId, String assigneeId, String actorId) {
        Ticket ticket = requireTicket(ticketId);

        Ticket assignedTicket = ticket.assignTo(assigneeId);
        Ticket savedTicket = ticketRepository.save(assignedTicket);

        publisher.publish(new TicketAssigned(ticketId, assignedTicket.boardId(), assigneeId, actorId, Instant.now()));
        return savedTicket;
    }

    @Transactional(readOnly = true)
    public Board getBoard(Long boardId) {
        return requireBoard(boardId);
    }

    @Transactional(readOnly = true)
    public Ticket getTicket(Long ticketId) {
        return requireTicket(ticketId);
    }

    @Transactional(readOnly = true)
    public List<Ticket> getBoardTickets(Long boardId) {
        requireBoard(boardId);
        return ticketRepository.findByBoardIdOrderBySwimlaneIdAscPositionAsc(boardId);
    }

    private Board requireBoard(Long boardId) {
        return boardRepository.findById(boardId).orElseThrow(() -> new BoardNotFoundException(boardId));
    }

    private Ticket requireTicket(Long ticketId) {
        return ticketRepository
                .findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }
}
