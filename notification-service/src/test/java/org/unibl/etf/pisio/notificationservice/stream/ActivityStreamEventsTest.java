package org.unibl.etf.pisio.notificationservice.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.service.BoardChanged;

class ActivityStreamEventsTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-25T10:00:00Z");

    @Test
    @DisplayName(
            """
            Given an addressed activity, \
            when its event is built, \
            then it is identified so a reconnect can resume\
            """)
    void addressedActivityCarriesItsId() {
        Activity activity = new Activity(8L, "event-8", 1L, "TicketAssigned", "Ticket assigned", "actor-1",
                "user-1", "actor-1 assigned \"Fix login\" to you", OCCURRED_AT, OCCURRED_AT);

        Set<DataWithMediaType> event = ActivityStreamEvents.notification(activity).build();

        assertThat(frameOf(event)).contains("id:8").contains("event:" + ActivityStreamEvents.NOTIFICATION);
    }

    @Test
    @DisplayName(
            """
            Given a board change, \
            when its event is built, \
            then it is unidentified so it never becomes a Last-Event-ID\
            """)
    void boardChangeCarriesNoId() {
        BoardChanged change = new BoardChanged(1L, "TicketMoved", "actor-1", OCCURRED_AT);

        Set<DataWithMediaType> event = ActivityStreamEvents.boardChanged(change).build();

        assertThat(frameOf(event)).contains("event:" + ActivityStreamEvents.BOARD_CHANGED).doesNotContain("id:");
        assertThat(event).extracting(DataWithMediaType::getData).contains(change);
    }

    private static String frameOf(Set<DataWithMediaType> event) {
        return event.stream()
                .map(DataWithMediaType::getData)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.joining());
    }
}
