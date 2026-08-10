package org.unibl.etf.pisio.boardservice.domain.event;

import java.time.Instant;

public record TicketAssigned(
        Long ticketId,
        Long boardId,
        String title,
        String assigneeId,
        String actorId,
        Instant occurredAt
) implements BoardEvent {

    public static final String TYPE = "TicketAssigned";

    @Override
    public String eventType() {
        return TYPE;
    }
}
