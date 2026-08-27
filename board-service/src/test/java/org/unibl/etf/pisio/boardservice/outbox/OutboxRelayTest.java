package org.unibl.etf.pisio.boardservice.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxRepository outbox;

    @Mock
    private ServiceBusSenderClient sender;

    @InjectMocks
    private OutboxRelay outboxRelay;

    @Test
    @DisplayName("Given no pending outbox entries, when relay is called, then nothing is sent or saved")
    void relayNoPendingEntries() {
        when(outbox.findByPublishedFalseOrderByIdAsc(any(Limit.class))).thenReturn(List.of());

        outboxRelay.relay();

        verifyNoInteractions(sender);
        verify(outbox, never()).saveAll(any());
    }

    @Test
    @DisplayName(
            """
            Given a pending outbox entry, \
            when relay is called, \
            then it is sent to Service Bus and marked published\
            """)
    void relaySinglePendingEntry() {
        OutboxEntry entry = new OutboxEntry(100L, "Ticket", "1", "TicketCreated",
                "{\"ticketId\":1}", Instant.parse("2026-07-25T10:00:00Z"), false);
        when(outbox.findByPublishedFalseOrderByIdAsc(any(Limit.class))).thenReturn(List.of(entry));

        outboxRelay.relay();

        ArgumentCaptor<ServiceBusMessage> messageCaptor = ArgumentCaptor.forClass(ServiceBusMessage.class);
        verify(sender).sendMessage(messageCaptor.capture());
        ServiceBusMessage message = messageCaptor.getValue();
        assertThat(message.getBody().toString()).hasToString("{\"ticketId\":1}");
        assertThat(message.getSubject()).isEqualTo("TicketCreated");
        assertThat(message.getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageId()).isEqualTo("100");
        assertThat(message.getApplicationProperties()).containsEntry("aggregateType", "Ticket");
        assertThat(message.getApplicationProperties()).containsEntry("aggregateId", "1");

        verify(outbox).saveAll(List.of(entry.markPublished()));
    }

    @Test
    @DisplayName(
            """
            Given multiple pending outbox entries, \
            when relay is called, \
            then all are sent and saved as published\
            """)
    void relayMultiplePendingEntries() {
        OutboxEntry first = new OutboxEntry(100L, "Ticket", "1", "TicketCreated",
                "{}", Instant.parse("2026-07-25T10:00:00Z"), false);
        OutboxEntry second = new OutboxEntry(101L, "Ticket", "2", "TicketMoved",
                "{}", Instant.parse("2026-07-25T10:01:00Z"), false);
        when(outbox.findByPublishedFalseOrderByIdAsc(any(Limit.class))).thenReturn(List.of(first, second));

        outboxRelay.relay();

        verify(sender, times(2)).sendMessage(any(ServiceBusMessage.class));
        verify(outbox).saveAll(List.of(first.markPublished(), second.markPublished()));
    }
}
