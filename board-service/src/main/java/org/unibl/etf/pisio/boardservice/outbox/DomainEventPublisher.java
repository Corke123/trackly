package org.unibl.etf.pisio.boardservice.outbox;

import org.springframework.stereotype.Component;
import org.unibl.etf.pisio.boardservice.domain.event.BoardEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class DomainEventPublisher {

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    public DomainEventPublisher(OutboxRepository outboxRepository, JsonMapper jsonMapper) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
    }

    public void publish(BoardEvent event) {
        try {
            String payload = jsonMapper.writeValueAsString(event);
            OutboxEntry entry = new OutboxEntry("Ticket", String.valueOf(event.ticketId()), event.eventType(), payload, event.occurredAt());
            outboxRepository.save(entry);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize domain event " + event.eventType(), e);
        }
    }
}
