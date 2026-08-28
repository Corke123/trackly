package org.unibl.etf.pisio.boardservice.exception;

public class CommentNotFoundException extends RuntimeException {

    public CommentNotFoundException(Long commentId) {
        super("Comment " + commentId + " not found");
    }
}
