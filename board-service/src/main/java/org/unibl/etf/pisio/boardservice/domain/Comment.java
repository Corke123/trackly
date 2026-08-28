package org.unibl.etf.pisio.boardservice.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("comment")
public record Comment(
        @Id Long id,
        Long ticketId,
        String authorId,
        String body,
        Instant createdAt
) {

    public Comment(Long ticketId, String authorId, String body) {
        this(null, ticketId, authorId, body, Instant.now());
    }

    public boolean isWrittenBy(String userId) {
        return this.authorId.equals(userId);
    }
}
