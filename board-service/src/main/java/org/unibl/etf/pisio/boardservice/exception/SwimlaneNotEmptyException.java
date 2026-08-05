package org.unibl.etf.pisio.boardservice.exception;

public class SwimlaneNotEmptyException extends RuntimeException {

    public SwimlaneNotEmptyException(Long swimlaneId, int ticketCount) {
        super("Swimlane " + swimlaneId + " still holds " + ticketCount + " ticket(s) and cannot be deleted");
    }
}
