package org.unibl.etf.pisio.notificationservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketAssigned;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketCreated;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketDeleted;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketMoved;
import org.unibl.etf.pisio.notificationservice.repository.ActivityRepository;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private ActivityIngestService activityIngestService;

    @Test
    @DisplayName("Given a new TicketCreated event, when ingest is called, then an activity summarizing the creation is persisted")
    void ingestTicketCreated() {
        String payload = "{\"ticketId\":100}";
        TicketCreated event = new TicketCreated(100L, 1L, 2L, "Title", "actor-1", OCCURRED_AT);
        when(activities.existsByEventId("event-1")).thenReturn(false);
        when(objectMapper.readValue(payload, TicketCreated.class)).thenReturn(event);
        echoSaves();

        ingestAt(() -> activityIngestService.ingest("event-1", TicketCreated.TYPE, payload));

        verify(activities).save(new Activity(null, "event-1", 1L, TicketCreated.TYPE,
                "Ticket #100 created: Title", "actor-1", null, null, OCCURRED_AT, RECORDED_AT));
    }

    @Test
    @DisplayName("Given a TicketMoved event for somebody else's ticket, when ingest is called, then the activity is addressed to the assignee")
    void ingestTicketMoved() {
        String payload = "{\"ticketId\":100}";
        TicketMoved event = new TicketMoved(100L, 1L, 2L, 3L, "Fix login", "Doing", "user-2", "actor-1", OCCURRED_AT);
        when(activities.existsByEventId("event-2")).thenReturn(false);
        when(objectMapper.readValue(payload, TicketMoved.class)).thenReturn(event);
        echoSaves();

        ingestAt(() -> activityIngestService.ingest("event-2", TicketMoved.TYPE, payload));

        verify(activities).save(new Activity(null, "event-2", 1L, TicketMoved.TYPE,
                "Ticket \"Fix login\" moved to Doing", "actor-1",
                "user-2", "actor-1 moved your ticket \"Fix login\" to Doing", OCCURRED_AT, RECORDED_AT));
    }

    @Test
    @DisplayName("Given a TicketMoved event for an unassigned ticket, when ingest is called, then the activity is addressed to nobody")
    void ingestTicketMovedWithoutAssignee() {
        String payload = "{\"ticketId\":100}";
        TicketMoved event = new TicketMoved(100L, 1L, 2L, 3L, "Fix login", "Doing", null, "actor-1", OCCURRED_AT);
        when(activities.existsByEventId("event-2")).thenReturn(false);
        when(objectMapper.readValue(payload, TicketMoved.class)).thenReturn(event);
        echoSaves();

        ingestAt(() -> activityIngestService.ingest("event-2", TicketMoved.TYPE, payload));

        verify(activities).save(recipientOf());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("Given a new TicketAssigned event, when ingest is called, then the activity is addressed to the new assignee")
    void ingestTicketAssigned() {
        String payload = "{\"ticketId\":100}";
        TicketAssigned event = new TicketAssigned(100L, 1L, "Fix login", "user-2", "actor-1", OCCURRED_AT);
        when(activities.existsByEventId("event-3")).thenReturn(false);
        when(objectMapper.readValue(payload, TicketAssigned.class)).thenReturn(event);
        echoSaves();

        ingestAt(() -> activityIngestService.ingest("event-3", TicketAssigned.TYPE, payload));

        verify(activities).save(new Activity(null, "event-3", 1L, TicketAssigned.TYPE,
                "Ticket \"Fix login\" assigned to user-2", "actor-1",
                "user-2", "actor-1 assigned \"Fix login\" to you", OCCURRED_AT, RECORDED_AT));
    }

    @Test
    @DisplayName("Given a user who assigned a ticket to themselves, when ingest is called, then the activity is addressed to nobody")
    void ingestSelfAssignment() {
        String payload = "{\"ticketId\":100}";
        TicketAssigned event = new TicketAssigned(100L, 1L, "Fix login", "user-2", "user-2", OCCURRED_AT);
        when(activities.existsByEventId("event-3")).thenReturn(false);
        when(objectMapper.readValue(payload, TicketAssigned.class)).thenReturn(event);
        echoSaves();

        ingestAt(() -> activityIngestService.ingest("event-3", TicketAssigned.TYPE, payload));

        verify(activities).save(recipientOf());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("Given a user who moved their own ticket, when ingest is called, then the activity is addressed to nobody")
    void ingestMoveOfOwnTicket() {
        String payload = "{\"ticketId\":100}";
        TicketMoved event = new TicketMoved(100L, 1L, 2L, 3L, "Fix login", "Doing", "user-2", "user-2", OCCURRED_AT);
        when(activities.existsByEventId("event-2")).thenReturn(false);
        when(objectMapper.readValue(payload, TicketMoved.class)).thenReturn(event);
        echoSaves();

        ingestAt(() -> activityIngestService.ingest("event-2", TicketMoved.TYPE, payload));

        verify(activities).save(recipientOf());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("Given an addressed activity, when ingest is called, then it is announced for delivery with the id it was saved under")
    void ingestAnnouncesAddressedActivity() {
        String payload = "{\"ticketId\":100}";
        TicketAssigned event = new TicketAssigned(100L, 1L, "Fix login", "user-2", "actor-1", OCCURRED_AT);
        when(activities.existsByEventId("event-3")).thenReturn(false);
        when(objectMapper.readValue(payload, TicketAssigned.class)).thenReturn(event);
        when(activities.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        ingestAt(() -> activityIngestService.ingest("event-3", TicketAssigned.TYPE, payload));

        ArgumentCaptor<ActivityRecorded> announced = ArgumentCaptor.forClass(ActivityRecorded.class);
        verify(events).publishEvent(announced.capture());
        assertThat(announced.getValue().activity().id()).isEqualTo(42L);
        assertThat(announced.getValue().activity().recipientId()).isEqualTo("user-2");
    }

    @Test
    @DisplayName("Given an event published before titles were carried, when ingest is called, then the ticket and swimlane are named by id")
    void ingestEventWithoutTitles() {
        String payload = "{\"ticketId\":100}";
        TicketMoved event = new TicketMoved(100L, 1L, 2L, 3L, null, null, "user-2", "actor-1", OCCURRED_AT);
        when(activities.existsByEventId("event-2")).thenReturn(false);
        when(objectMapper.readValue(payload, TicketMoved.class)).thenReturn(event);
        echoSaves();

        ingestAt(() -> activityIngestService.ingest("event-2", TicketMoved.TYPE, payload));

        verify(activities).save(new Activity(null, "event-2", 1L, TicketMoved.TYPE,
                "Ticket #100 moved to swimlane 3", "actor-1",
                "user-2", "actor-1 moved your ticket #100 to swimlane 3", OCCURRED_AT, RECORDED_AT));
    }

    @Test
    @DisplayName("Given an event carrying blank titles, when ingest is called, then the ticket and swimlane are named by id")
    void ingestEventWithBlankTitles() {
        String payload = "{\"ticketId\":100}";
        TicketMoved event = new TicketMoved(100L, 1L, 2L, 3L, "  ", "", "user-2", "actor-1", OCCURRED_AT);
        when(activities.existsByEventId("event-2")).thenReturn(false);
        when(objectMapper.readValue(payload, TicketMoved.class)).thenReturn(event);
        echoSaves();

        ingestAt(() -> activityIngestService.ingest("event-2", TicketMoved.TYPE, payload));

        verify(activities).save(new Activity(null, "event-2", 1L, TicketMoved.TYPE,
                "Ticket #100 moved to swimlane 3", "actor-1",
                "user-2", "actor-1 moved your ticket #100 to swimlane 3", OCCURRED_AT, RECORDED_AT));
    }

    @Test
    @DisplayName("Given an event id that was already recorded, when ingest is called, then the payload is not parsed and nothing is persisted")
    void ingestAlreadyRecordedEvent() {
        when(activities.existsByEventId("event-1")).thenReturn(true);

        activityIngestService.ingest("event-1", TicketCreated.TYPE, "{\"ticketId\":100}");

        verify(activities, never()).save(any());
        verifyNoInteractions(objectMapper, events);
    }

    @Test
    @DisplayName("Given an event without an id, when ingest is called, then no duplicate check is performed and the activity is persisted")
    void ingestEventWithoutId() {
        String payload = "{\"ticketId\":100}";
        TicketCreated event = new TicketCreated(100L, 1L, 2L, "Title", "actor-1", OCCURRED_AT);
        when(objectMapper.readValue(payload, TicketCreated.class)).thenReturn(event);
        echoSaves();

        ingestAt(() -> activityIngestService.ingest(null, TicketCreated.TYPE, payload));

        verify(activities, never()).existsByEventId(any());
        verify(activities).save(new Activity(null, null, 1L, TicketCreated.TYPE,
                "Ticket #100 created: Title", "actor-1", null, null, OCCURRED_AT, RECORDED_AT));
    }

    @Test
    @DisplayName("Given a TicketDeleted event for somebody else's ticket, when ingest is called, then the activity is addressed to the former assignee")
    void ingestTicketDeleted() {
        String payload = "{\"ticketId\":100}";
        TicketDeleted event = new TicketDeleted(100L, 1L, 2L, "Fix login", "To Do", "user-2", "actor-1", OCCURRED_AT);
        when(activities.existsByEventId("event-4")).thenReturn(false);
        when(objectMapper.readValue(payload, TicketDeleted.class)).thenReturn(event);
        echoSaves();

        ingestAt(() -> activityIngestService.ingest("event-4", TicketDeleted.TYPE, payload));

        verify(activities).save(new Activity(null, "event-4", 1L, TicketDeleted.TYPE,
                "Ticket \"Fix login\" deleted from To Do", "actor-1",
                "user-2", "actor-1 deleted your ticket \"Fix login\"", OCCURRED_AT, RECORDED_AT));
    }

    @Test
    @DisplayName("Given a TicketDeleted event for an unassigned ticket, when ingest is called, then the activity is addressed to nobody")
    void ingestTicketDeletedWithoutAssignee() {
        String payload = "{\"ticketId\":100}";
        TicketDeleted event = new TicketDeleted(100L, 1L, 2L, "Fix login", "To Do", null, "actor-1", OCCURRED_AT);
        when(activities.existsByEventId("event-4")).thenReturn(false);
        when(objectMapper.readValue(payload, TicketDeleted.class)).thenReturn(event);
        echoSaves();

        ingestAt(() -> activityIngestService.ingest("event-4", TicketDeleted.TYPE, payload));

        verify(activities).save(recipientOf());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("Given an unknown event type, when ingest is called, then IllegalStateException is thrown and nothing is persisted")
    void ingestUnknownEventType() {
        when(activities.existsByEventId("event-1")).thenReturn(false);

        assertThatThrownBy(() -> activityIngestService.ingest("event-1", "TicketArchived", "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TicketArchived")
                .hasCauseInstanceOf(IllegalArgumentException.class);

        verify(activities, never()).save(any());
        verifyNoInteractions(events);
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
        verifyNoInteractions(events);
    }

    private void echoSaves() {
        when(activities.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Activity withId(Activity activity) {
        return new Activity(42L, activity.eventId(), activity.boardId(), activity.type(), activity.summary(),
                activity.actorId(), activity.recipientId(), activity.recipientMessage(),
                activity.occurredAt(), activity.recordedAt());
    }

    private static Activity recipientOf() {
        return argThat(activity -> activity != null && Objects.equals(activity.recipientId(), null) && activity.recipientMessage() == null);
    }

    private static void ingestAt(Runnable ingest) {
        try (MockedStatic<Instant> instant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            instant.when(Instant::now).thenReturn(ActivityIngestServiceTest.RECORDED_AT);
            ingest.run();
        }
    }
}
