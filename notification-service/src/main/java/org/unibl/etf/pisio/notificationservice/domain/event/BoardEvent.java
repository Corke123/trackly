package org.unibl.etf.pisio.notificationservice.domain.event;

import java.time.Instant;

public sealed interface BoardEvent permits TicketCreated, TicketMoved, TicketAssigned, TicketDeleted {

    String eventType();

    Long ticketId();

    Long boardId();

    String actorId();

    Instant occurredAt();
}
