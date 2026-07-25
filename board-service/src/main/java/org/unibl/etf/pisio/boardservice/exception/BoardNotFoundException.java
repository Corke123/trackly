package org.unibl.etf.pisio.boardservice.exception;

public class BoardNotFoundException extends RuntimeException {

    public BoardNotFoundException(Long boardId) {
        super("Board " + boardId + " not found");
    }
}
