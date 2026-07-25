package org.unibl.etf.pisio.boardservice.outbox;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("outbox")
public record OutboxEntry(
        @Id Long id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        Instant occurredAt,
        boolean published
) {

    public OutboxEntry(String aggregateType, String aggregateId, String eventType, String payload, Instant occurredAt) {
        this(null, aggregateType, aggregateId, eventType, payload, occurredAt, false);
    }

    public OutboxEntry markPublished() {
        return new OutboxEntry(
                this.id,
                this.aggregateType,
                this.aggregateId,
                this.eventType,
                this.payload,
                this.occurredAt,
                true
        );
    }
}
