package org.unibl.etf.pisio.boardservice.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unibl.etf.pisio.boardservice.domain.event.TicketCreated;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DomainEventPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private JsonMapper jsonMapper;

    @InjectMocks
    private DomainEventPublisher domainEventPublisher;

    @Test
    @DisplayName("Given a domain event, when publish is called, then a serialized outbox entry is persisted")
    void publish() {
        TicketCreated event = new TicketCreated(100L, 1L, 2L, "Title", "actor-1", Instant.parse("2026-07-25T10:00:00Z"));
        when(jsonMapper.writeValueAsString(event)).thenReturn("{\"ticketId\":100}");

        domainEventPublisher.publish(event);

        verify(outboxRepository).save(new OutboxEntry(null, "Ticket", "100", TicketCreated.TYPE, "{\"ticketId\":100}", event.occurredAt(), false));
    }

    @Test
    @DisplayName("Given an event that fails to serialize, when publish is called, then IllegalStateException is thrown and nothing is saved")
    void publishSerializationFailure() {
        TicketCreated event = new TicketCreated(100L, 1L, 2L, "Title", "actor-1", Instant.parse("2026-07-25T10:00:00Z"));
        when(jsonMapper.writeValueAsString(event)).thenThrow(JacksonIOException.construct(new IOException("boom")));

        assertThatThrownBy(() -> domainEventPublisher.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TicketCreated.TYPE)
                .hasCauseInstanceOf(JacksonIOException.class);

        verifyNoInteractions(outboxRepository);
    }
}
