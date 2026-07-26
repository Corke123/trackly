package org.unibl.etf.pisio.notificationservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketAssigned;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketCreated;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketMoved;
import org.unibl.etf.pisio.notificationservice.repository.ActivityRepository;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityIngestServiceTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-25T10:00:00Z");
    private static final Instant RECORDED_AT = Instant.parse("2026-07-25T10:00:01Z");

    @Mock
    private ActivityRepository activities;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ActivityIngestService activityIngestService;

    @Test
    @DisplayName("Given a new TicketCreated event, when ingest is called, then an activity summarizing the creation is persisted")
    void ingestTicketCreated() {
        String payload = "{\"ticketId\":100}";
        TicketCreated event = new TicketCreated(100L, 1L, 2L, "Title", "actor-1", OCCURRED_AT);
        when(activities.existsByEventId("event-1")).thenReturn(false);
        when(objectMapper.readValue(payload, TicketCreated.class)).thenReturn(event);

        ingestAt(RECORDED_AT, () -> activityIngestService.ingest("event-1", TicketCreated.TYPE, payload));

        verify(activities).save(new Activity(null, "event-1", 1L, TicketCreated.TYPE,
                "Ticket #100 created: Title", "actor-1", OCCURRED_AT, RECORDED_AT));
    }

    @Test
    @DisplayName("Given a new TicketMoved event, when ingest is called, then an activity summarizing the move is persisted")
    void ingestTicketMoved() {
        String payload = "{\"ticketId\":100}";
        TicketMoved event = new TicketMoved(100L, 1L, 2L, 3L, "actor-1", OCCURRED_AT);
        when(activities.existsByEventId("event-2")).thenReturn(false);
        when(objectMapper.readValue(payload, TicketMoved.class)).thenReturn(event);

        ingestAt(RECORDED_AT, () -> activityIngestService.ingest("event-2", TicketMoved.TYPE, payload));

        verify(activities).save(new Activity(null, "event-2", 1L, TicketMoved.TYPE,
                "Ticket #100 moved to swimlane 3", "actor-1", OCCURRED_AT, RECORDED_AT));
    }

    @Test
    @DisplayName("Given a new TicketAssigned event, when ingest is called, then an activity summarizing the assignment is persisted")
    void ingestTicketAssigned() {
        String payload = "{\"ticketId\":100}";
        TicketAssigned event = new TicketAssigned(100L, 1L, "user-2", "actor-1", OCCURRED_AT);
        when(activities.existsByEventId("event-3")).thenReturn(false);
        when(objectMapper.readValue(payload, TicketAssigned.class)).thenReturn(event);

        ingestAt(RECORDED_AT, () -> activityIngestService.ingest("event-3", TicketAssigned.TYPE, payload));

        verify(activities).save(new Activity(null, "event-3", 1L, TicketAssigned.TYPE,
                "Ticket #100 assigned to user-2", "actor-1", OCCURRED_AT, RECORDED_AT));
    }

    @Test
    @DisplayName("Given an event id that was already recorded, when ingest is called, then the payload is not parsed and nothing is persisted")
    void ingestAlreadyRecordedEvent() {
        when(activities.existsByEventId("event-1")).thenReturn(true);

        activityIngestService.ingest("event-1", TicketCreated.TYPE, "{\"ticketId\":100}");

        verify(activities, never()).save(any());
        verifyNoInteractions(objectMapper);
    }

    @Test
    @DisplayName("Given an event without an id, when ingest is called, then no duplicate check is performed and the activity is persisted")
    void ingestEventWithoutId() {
        String payload = "{\"ticketId\":100}";
        TicketCreated event = new TicketCreated(100L, 1L, 2L, "Title", "actor-1", OCCURRED_AT);
        when(objectMapper.readValue(payload, TicketCreated.class)).thenReturn(event);

        ingestAt(RECORDED_AT, () -> activityIngestService.ingest(null, TicketCreated.TYPE, payload));

        verify(activities, never()).existsByEventId(any());
        verify(activities).save(new Activity(null, null, 1L, TicketCreated.TYPE,
                "Ticket #100 created: Title", "actor-1", OCCURRED_AT, RECORDED_AT));
    }

    @Test
    @DisplayName("Given an unknown event type, when ingest is called, then IllegalStateException is thrown and nothing is persisted")
    void ingestUnknownEventType() {
        when(activities.existsByEventId("event-1")).thenReturn(false);

        assertThatThrownBy(() -> activityIngestService.ingest("event-1", "TicketDeleted", "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TicketDeleted")
                .hasCauseInstanceOf(IllegalArgumentException.class);

        verify(activities, never()).save(any());
    }

    @Test
    @DisplayName("Given a payload that fails to deserialize, when ingest is called, then IllegalStateException is thrown and nothing is persisted")
    void ingestDeserializationFailure() {
        when(activities.existsByEventId("event-1")).thenReturn(false);
        when(objectMapper.readValue("not-json", TicketCreated.class))
                .thenThrow(JacksonIOException.construct(new IOException("boom")));

        assertThatThrownBy(() -> activityIngestService.ingest("event-1", TicketCreated.TYPE, "not-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TicketCreated.TYPE)
                .hasCauseInstanceOf(JacksonIOException.class);

        verify(activities, never()).save(any());
    }

    private static void ingestAt(Instant now, Runnable ingest) {
        try (MockedStatic<Instant> instant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            instant.when(Instant::now).thenReturn(now);
            ingest.run();
        }
    }
}
