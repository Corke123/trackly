package org.unibl.etf.pisio.boardservice.exception;

public class CommentNotYoursException extends RuntimeException {

    public CommentNotYoursException(Long commentId) {
        super("Comment " + commentId + " was written by somebody else");
    }
}
