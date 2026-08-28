package org.unibl.etf.pisio.boardservice.controller.dto;

import java.time.Instant;
import org.unibl.etf.pisio.boardservice.domain.Comment;

public record CommentView(Long id, Long ticketId, String authorId, String body, Instant createdAt) {

    public static CommentView of(Comment comment) {
        return new CommentView(comment.id(), comment.ticketId(), comment.authorId(), comment.body(),
                comment.createdAt());
    }
}
