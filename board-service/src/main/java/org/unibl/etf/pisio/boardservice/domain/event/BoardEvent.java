package org.unibl.etf.pisio.boardservice.domain.event;

import java.time.Instant;

public sealed interface BoardEvent permits TicketCreated, TicketMoved, TicketAssigned {

    String eventType();

    Long ticketId();

    Instant occurredAt();
}
