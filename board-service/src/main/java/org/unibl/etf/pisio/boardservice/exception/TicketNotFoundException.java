package org.unibl.etf.pisio.boardservice.exception;

public class TicketNotFoundException extends RuntimeException {

    public TicketNotFoundException(Long ticketId) {
        super("Ticket " + ticketId + " not found");
    }
}
