package org.unibl.etf.pisio.boardservice.domain.event;

import java.time.Instant;

public record TicketCommented(
        Long ticketId,
        Long boardId,
        Long commentId,
        String title,
        String assigneeId,
        String actorId,
        Instant occurredAt
) implements BoardEvent {

    public static final String TYPE = "TicketCommented";

    @Override
    public String eventType() {
        return TYPE;
    }
}
