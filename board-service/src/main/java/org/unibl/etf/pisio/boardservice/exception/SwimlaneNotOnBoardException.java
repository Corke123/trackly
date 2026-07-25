package org.unibl.etf.pisio.boardservice.exception;

public class SwimlaneNotOnBoardException extends RuntimeException {

    public SwimlaneNotOnBoardException(Long boardId, Long swimlaneId) {
        super("Swimlane " + swimlaneId + " not on the board " + boardId);
    }
}
