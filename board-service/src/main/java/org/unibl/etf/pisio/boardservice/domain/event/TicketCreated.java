package org.unibl.etf.pisio.boardservice.domain.event;

import java.time.Instant;

public record TicketCreated(
        Long ticketId,
        Long boardId,
        Long swimlaneId,
        String title,
        String actorId,
        Instant occurredAt
) implements BoardEvent {

    public static final String TYPE = "TicketCreated";

    @Override
    public String eventType() {
        return TYPE;
    }
}
