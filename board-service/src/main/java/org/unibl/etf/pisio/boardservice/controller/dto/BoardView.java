package org.unibl.etf.pisio.boardservice.controller.dto;

import org.jspecify.annotations.NonNull;
import org.unibl.etf.pisio.boardservice.domain.Board;
import org.unibl.etf.pisio.boardservice.domain.Swimlane;
import org.unibl.etf.pisio.boardservice.domain.Ticket;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record BoardView(Long id, String name, List<SwimlaneView> swimlanes) {

    public record SwimlaneView(Long id, String title, List<BoardView.TicketView> tickets) {

        public static SwimlaneView of(Swimlane lane) {
            return new SwimlaneView(lane.id(), lane.title(), List.of());
        }
    }

    public record TicketView(Long id, String title, String description, String assigneeId, int position) {

        public static TicketView of(Ticket ticket) {
            return new TicketView(ticket.id(), ticket.title(), ticket.description(), ticket.assigneeId(), ticket.position());
        }
    }

    public static BoardView of(Board board, List<Ticket> tickets) {
        Map<Long, List<Ticket>> bySwimlane = tickets.stream()
                .collect(Collectors.groupingBy(Ticket::swimlaneId));

        List<SwimlaneView> lanes = board.swimlanes().stream()
                .map(lane -> createSwimlaneView(lane, bySwimlane))
                .toList();

        return new BoardView(board.id(), board.name(), lanes);
    }

    private static @NonNull SwimlaneView createSwimlaneView(Swimlane lane, Map<Long, List<Ticket>> bySwimlane) {
        return new SwimlaneView(lane.id(), lane.title(), bySwimlane.getOrDefault(lane.id(), List.of()).stream()
                .map(TicketView::of)
                .toList());
    }
}
