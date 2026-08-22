package org.unibl.etf.pisio.boardservice.domain.event;

import java.time.Instant;

public sealed interface BoardEvent permits TicketCreated, TicketMoved, TicketAssigned, TicketDeleted {

    String eventType();

    Long ticketId();

    Instant occurredAt();
}
