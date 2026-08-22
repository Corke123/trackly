package org.unibl.etf.pisio.notificationservice.domain.event;

import java.time.Instant;

public record TicketDeleted(
        Long ticketId,
        Long boardId,
        Long swimlaneId,
        String title,
        String swimlaneTitle,
        String assigneeId,
        String actorId,
        Instant occurredAt
) implements BoardEvent {

    public static final String TYPE = "TicketDeleted";

    @Override
    public String eventType() {
        return TYPE;
    }
}
