package org.unibl.etf.pisio.boardservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unibl.etf.pisio.boardservice.domain.Board;
import org.unibl.etf.pisio.boardservice.domain.Comment;
import org.unibl.etf.pisio.boardservice.domain.Swimlane;
import org.unibl.etf.pisio.boardservice.domain.Ticket;
import org.unibl.etf.pisio.boardservice.domain.event.TicketAssigned;
import org.unibl.etf.pisio.boardservice.domain.event.TicketCommented;
import org.unibl.etf.pisio.boardservice.domain.event.TicketCreated;
import org.unibl.etf.pisio.boardservice.domain.event.TicketDeleted;
import org.unibl.etf.pisio.boardservice.domain.event.TicketMoved;
import org.unibl.etf.pisio.boardservice.exception.BoardNotFoundException;
import org.unibl.etf.pisio.boardservice.exception.CommentNotFoundException;
import org.unibl.etf.pisio.boardservice.exception.CommentNotYoursException;
import org.unibl.etf.pisio.boardservice.exception.SwimlaneNotEmptyException;
import org.unibl.etf.pisio.boardservice.exception.SwimlaneNotOnBoardException;
import org.unibl.etf.pisio.boardservice.exception.TicketNotFoundException;
import org.unibl.etf.pisio.boardservice.outbox.DomainEventPublisher;
import org.unibl.etf.pisio.boardservice.repository.BoardRepository;
import org.unibl.etf.pisio.boardservice.repository.CommentRepository;
import org.unibl.etf.pisio.boardservice.repository.TicketRepository;

@Service
@Transactional
public class BoardService {

    private final BoardRepository boardRepository;
    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final DomainEventPublisher publisher;

    public BoardService(BoardRepository boardRepository, TicketRepository ticketRepository,
                        CommentRepository commentRepository, DomainEventPublisher publisher) {
        this.boardRepository = boardRepository;
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.publisher = publisher;
    }

    public Board createBoard(String name) {
        return boardRepository.save(new Board(name));
    }

    public Board renameBoard(Long boardId, String name) {
        Board board = requireBoard(boardId);
        return boardRepository.save(board.rename(name));
    }

    public Swimlane addSwimlane(Long boardId, String title) {
        Board board = requireBoard(boardId);
        Set<Long> idsBefore = board.swimlanes().stream().map(Swimlane::id).collect(Collectors.toSet());

        Board boardWithNewSwimlane = board.addSwimlane(title);
        Board updatedBoard = boardRepository.save(boardWithNewSwimlane);

        return updatedBoard.swimlanes().stream()
                .filter(swimlane -> !idsBefore.contains(swimlane.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Swimlane '" + title + "' is missing from board " + boardId + " after it was saved"));
    }

    /**
     * A swimlane that still holds tickets is never deleted — the tickets would be orphaned, and the
     * board owner almost certainly meant to move them first.
     */
    public void deleteSwimlane(Long boardId, Long swimlaneId) {
        Board board = requireBoard(boardId);

        int ticketCount = ticketRepository.countBySwimlaneId(swimlaneId);
        if (ticketCount > 0) {
            throw new SwimlaneNotEmptyException(swimlaneId, ticketCount);
        }

        boardRepository.save(board.removeSwimlane(swimlaneId));
    }

    public Board reorderSwimlanes(Long boardId, List<Long> orderedSwimlaneIds) {
        Board board = requireBoard(boardId);
        Board reordered = board.reorderSwimlanes(orderedSwimlaneIds);

        // Persist the order the board just validated, not the one that was asked for.
        boardRepository.updateSwimlaneOrder(boardId, reordered.swimlanes().stream().map(Swimlane::id).toList());

        return reordered;
    }

    public Ticket createTicket(Long boardId, Long swimlaneId, String title, String description, String actorId) {
        Board board = requireBoard(boardId);

        if (board.hasNotSwimlane(swimlaneId)) {
            throw new SwimlaneNotOnBoardException(boardId, swimlaneId);
        }

        int position = ticketRepository.countBySwimlaneId(swimlaneId);
        Ticket ticket = ticketRepository.save(new Ticket(boardId, swimlaneId, title, description, position));

        publisher.publish(new TicketCreated(ticket.id(), boardId, swimlaneId, title, actorId, Instant.now()));
        return ticket;
    }

    /**
     * Moves a ticket to {@code toPosition} within {@code toSwimlaneId} and renumbers the surrounding tickets
     * around it, so the stored positions stay a dense 0…n-1 sequence per swimlane — which is what
     * the board view relies on to render a stable order after a drag.
     */
    public Ticket moveTicket(Long ticketId, Long toSwimlaneId, int toPosition, String actorId) {
        Ticket ticket = requireTicket(ticketId);
        Board board = requireBoard(ticket.boardId());

        if (board.hasNotSwimlane(toSwimlaneId)) {
            throw new SwimlaneNotOnBoardException(ticket.boardId(), toSwimlaneId);
        }

        Long from = ticket.swimlaneId();

        List<Ticket> target = new ArrayList<>(ticketRepository.findBySwimlaneIdOrderByPositionAsc(toSwimlaneId));
        target.removeIf(candidate -> candidate.id().equals(ticketId));

        // A position past the end of the swimlane means "last", not a rejection.
        int landedAt = Math.min(toPosition, target.size());
        Ticket movedTicket = ticket.moveTo(toSwimlaneId, landedAt);
        target.add(landedAt, movedTicket);

        if (!from.equals(toSwimlaneId)) {
            List<Ticket> source = ticketRepository.findBySwimlaneIdOrderByPositionAsc(from).stream()
                    .filter(candidate -> !candidate.id().equals(ticketId))
                    .toList();
            ticketRepository.saveAll(renumbered(source, null));
        }

        ticketRepository.saveAll(renumbered(target, ticketId));

        publisher.publish(new TicketMoved(ticketId, movedTicket.boardId(), from, toSwimlaneId,
                movedTicket.title(), board.swimlaneTitle(toSwimlaneId), movedTicket.assigneeId(),
                actorId, Instant.now()));
        return movedTicket;
    }

    /**
     * The tickets the new order actually changes. Every save is a statement of its own, so writing a
     * whole swimlane back to move one ticket within it costs a row per ticket, while a drag usually
     * shifts only the span between the old index and the new one.
     *
     * <p>{@code alwaysWriteId} names the ticket being moved: it has to be written even when it lands
     * on the position number it already had, because its swimlane may be what changed.
     */
    private List<Ticket> renumbered(List<Ticket> ordered, Long alwaysWriteId) {
        return IntStream.range(0, ordered.size())
                .filter(index -> ordered.get(index).position() != index
                        || ordered.get(index).id().equals(alwaysWriteId))
                .mapToObj(index -> ordered.get(index).atPosition(index))
                .toList();
    }

    public Ticket assignTicket(Long ticketId, String assigneeId, String actorId) {
        Ticket ticket = requireTicket(ticketId);

        Ticket assignedTicket = ticket.assignTo(assigneeId);
        Ticket savedTicket = ticketRepository.save(assignedTicket);

        publisher.publish(new TicketAssigned(ticketId, assignedTicket.boardId(), assignedTicket.title(),
                assigneeId, actorId, Instant.now()));
        return savedTicket;
    }

    public void deleteTicket(Long ticketId, String actorId) {
        Ticket ticket = requireTicket(ticketId);
        commentRepository.deleteByTicketId(ticketId);

        Board board = requireBoard(ticket.boardId());
        ticketRepository.delete(ticket);

        List<Ticket> remaining = ticketRepository.findBySwimlaneIdOrderByPositionAsc(ticket.swimlaneId()).stream()
                .filter(candidate -> !candidate.id().equals(ticketId))
                .toList();
        ticketRepository.saveAll(renumbered(remaining, null));

        publisher.publish(new TicketDeleted(ticketId, ticket.boardId(), ticket.swimlaneId(),
                ticket.title(), board.swimlaneTitle(ticket.swimlaneId()), ticket.assigneeId(),
                actorId, Instant.now()));
    }

    public Comment postComment(Long ticketId, String body, String authorId) {
        Ticket ticket = requireTicket(ticketId);
        Comment comment = commentRepository.save(new Comment(ticketId, authorId, body));

        publisher.publish(new TicketCommented(ticketId, ticket.boardId(), comment.id(), ticket.title(),
                ticket.assigneeId(), authorId, Instant.now()));
        return comment;
    }

    public void deleteComment(Long ticketId, Long commentId, String actorId, boolean actorIsAdmin) {
        Comment comment = requireComment(ticketId, commentId);

        if (!actorIsAdmin && !comment.isWrittenBy(actorId)) {
            throw new CommentNotYoursException(commentId);
        }

        commentRepository.delete(comment);
    }

    @Transactional(readOnly = true)
    public List<Comment> getComments(Long ticketId) {
        requireTicket(ticketId);
        return commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId);
    }

    @Transactional(readOnly = true)
    public List<Board> listBoards() {
        return boardRepository.findAll();
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

    private Comment requireComment(Long ticketId, Long commentId) {
        Comment comment = commentRepository
                .findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));

        if (!comment.ticketId().equals(ticketId)) {
            throw new CommentNotFoundException(commentId);
        }

        return comment;
    }

    private Ticket requireTicket(Long ticketId) {
        return ticketRepository
                .findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }
}
