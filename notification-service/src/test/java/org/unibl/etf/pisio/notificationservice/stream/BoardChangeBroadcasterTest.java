package org.unibl.etf.pisio.notificationservice.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.unibl.etf.pisio.notificationservice.service.BoardChanged;

@ExtendWith(MockitoExtension.class)
class BoardChangeBroadcasterTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-25T10:00:00Z");

    @Mock
    private ActivityStreamRegistry registry;

    @InjectMocks
    private BoardChangeBroadcaster broadcaster;

    @Test
    @DisplayName("Given a board change, when it is broadcast, then it goes to everyone watching the board")
    void broadcastsToEveryone() {
        broadcaster.onBoardChanged(new BoardChanged(1L, "TicketMoved", "actor-1", OCCURRED_AT));

        verify(registry).broadcast(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName(
            """
            Given the broadcast listener, \
            when its transaction phase is inspected, \
            then it only runs after commit\
            """)
    void onlyBroadcastsCommittedChanges() throws NoSuchMethodException {
        TransactionalEventListener listener = BoardChangeBroadcaster.class
                .getMethod("onBoardChanged", BoardChanged.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
