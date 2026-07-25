package org.unibl.etf.pisio.boardservice.domain.event;

import java.time.Instant;

public record TicketMoved(
        Long ticketId,
        Long boardId,
        Long fromSwimlaneId,
        Long toSwimlaneId,
        String actorId,
        Instant occurredAt
) implements BoardEvent {

    public static final String TYPE = "TicketMoved";

    @Override
    public String eventType() {
        return TYPE;
    }
}
