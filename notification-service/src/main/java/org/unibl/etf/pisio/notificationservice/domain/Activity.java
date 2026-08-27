package org.unibl.etf.pisio.notificationservice.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("activity")
public record Activity(
        @Id Long id,
        String eventId,
        Long boardId,
        String type,
        String summary,
        String actorId,
        String recipientId,
        String recipientMessage,
        Instant occurredAt,
        Instant recordedAt
) {

    public Activity(String eventId, Long boardId, String type, String summary, String actorId,
                    String recipientId, String recipientMessage, Instant occurredAt) {
        this(null, eventId, boardId, type, summary, actorId, recipientId, recipientMessage, occurredAt, Instant.now());
    }

    public boolean isAddressed() {
        return recipientId != null;
    }
}
