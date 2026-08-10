package org.unibl.etf.pisio.notificationservice.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.service.ActivityRecorded;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ActivityBroadcasterTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-25T10:00:00Z");

    @Mock
    private ActivityStreamRegistry registry;

    @InjectMocks
    private ActivityBroadcaster broadcaster;

    @Test
    @DisplayName("Given a recorded activity, when it is broadcast, then it goes to the recipient it is addressed to")
    void deliversToTheRecipient() {
        broadcaster.onActivityRecorded(new ActivityRecorded(addressed(8L, "user-1")));

        verify(registry).send(eq("user-1"), any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("Given the broadcast listener, when its transaction phase is inspected, then it only runs after commit")
    void onlyDeliversCommittedActivities() throws NoSuchMethodException {
        TransactionalEventListener listener = ActivityBroadcaster.class
                .getMethod("onActivityRecorded", ActivityRecorded.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    private static Activity addressed(Long id, String recipientId) {
        return new Activity(id, "event-" + id, 1L, "TicketAssigned", "Ticket \"Fix login\" assigned to " + recipientId,
                "actor-1", recipientId, "actor-1 assigned \"Fix login\" to you", OCCURRED_AT, OCCURRED_AT);
    }
}
