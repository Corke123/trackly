package org.unibl.etf.pisio.notificationservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unibl.etf.pisio.notificationservice.controller.ActivityController.ActivityView;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketCreated;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketMoved;
import org.unibl.etf.pisio.notificationservice.repository.ActivityRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityControllerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-25T10:00:00Z");

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private ActivityController activityController;

    @Test
    @DisplayName("Given no board id, when feed is called, then the latest activities across all boards are returned")
    void feedWithoutBoardId() {
        Activity first = activity(1L, 10L, TicketCreated.TYPE, "Ticket #100 created: Title", OCCURRED_AT);
        Activity second = activity(2L, 20L, TicketMoved.TYPE, "Ticket #101 moved to swimlane 3", OCCURRED_AT.minusSeconds(60));
        when(activityRepository.findByOrderByOccurredAtDesc(ActivityController.FEED_SIZE))
                .thenReturn(List.of(first, second));

        List<ActivityView> result = activityController.feed(null);

        assertThat(result).containsExactly(
                new ActivityView(1L, 10L, TicketCreated.TYPE, "Ticket #100 created: Title", "actor-1", OCCURRED_AT),
                new ActivityView(2L, 20L, TicketMoved.TYPE, "Ticket #101 moved to swimlane 3", "actor-1", OCCURRED_AT.minusSeconds(60))
        );
        verifyNoMoreInteractions(activityRepository);
    }

    @Test
    @DisplayName("Given a board id, when feed is called, then only that board's latest activities are returned")
    void feedWithBoardId() {
        Activity activity = activity(1L, 10L, TicketCreated.TYPE, "Ticket #100 created: Title", OCCURRED_AT);
        when(activityRepository.findByBoardIdOrderByOccurredAtDesc(10L, ActivityController.FEED_SIZE))
                .thenReturn(List.of(activity));

        List<ActivityView> result = activityController.feed(10L);

        assertThat(result).containsExactly(
                new ActivityView(1L, 10L, TicketCreated.TYPE, "Ticket #100 created: Title", "actor-1", OCCURRED_AT)
        );
        verifyNoMoreInteractions(activityRepository);
    }

    @Test
    @DisplayName("Given a board without recorded activity, when feed is called, then an empty feed is returned")
    void feedWithoutActivity() {
        when(activityRepository.findByBoardIdOrderByOccurredAtDesc(10L, ActivityController.FEED_SIZE))
                .thenReturn(List.of());

        assertThat(activityController.feed(10L)).isEmpty();
    }

    @Test
    @DisplayName("Given the feed endpoint, when the page size is inspected, then it is capped at 50 entries")
    void feedSizeIsCapped() {
        assertThat(ActivityController.FEED_SIZE.max()).isEqualTo(50);
    }

    private static Activity activity(Long id, Long boardId, String type, String summary, Instant occurredAt) {
        return new Activity(id, "event-" + id, boardId, type, summary, "actor-1", null, null,
                occurredAt, occurredAt.plusSeconds(1));
    }
}
