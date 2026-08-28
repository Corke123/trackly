package org.unibl.etf.pisio.boardservice.domain.event;

import java.time.Instant;

public sealed interface BoardEvent permits TicketCreated, TicketMoved, TicketAssigned, TicketCommented, TicketDeleted {

    String eventType();

    Long ticketId();

    Instant occurredAt();
}
