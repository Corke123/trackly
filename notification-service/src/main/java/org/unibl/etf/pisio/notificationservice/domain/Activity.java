package org.unibl.etf.pisio.notificationservice.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("activity")
public record Activity(
        @Id Long id,
        String eventId,
        Long boardId,
        String type,
        String summary,
        String actorId,
        Instant occurredAt,
        Instant recordedAt
) {

    public Activity(String eventId, Long boardId, String type, String summary, String actorId, Instant occurredAt) {
        this(null, eventId, boardId, type, summary, actorId, occurredAt, Instant.now());
    }

}
