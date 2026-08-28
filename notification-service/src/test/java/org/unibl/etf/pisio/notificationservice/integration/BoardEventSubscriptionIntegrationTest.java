package org.unibl.etf.pisio.notificationservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.unibl.etf.pisio.notificationservice.integration.NotificationIntegrationTestSupport.awaitActivity;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketAssigned;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketCommented;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketCreated;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketMoved;
import org.unibl.etf.pisio.notificationservice.integration.ServiceBusTestSupportConfig.BoardEventTestPublisher;
import org.unibl.etf.pisio.notificationservice.repository.ActivityRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, ServiceBusTestSupportConfig.class})
@ActiveProfiles("local")
class BoardEventSubscriptionIntegrationTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-25T10:00:00Z");

    @Autowired
    private BoardEventTestPublisher publisher;

    @Autowired
    private ActivityRepository activityRepository;

    @Test
    @DisplayName(
            """
            Given a TicketCreated event on the topic, \
            when the subscription reads it, \
            then the activity is recorded with a creation summary\
            """)
    void readsTicketCreatedEvent() {
        publisher.publish("sub-1", new TicketCreated(100L, 1L, 10L, "Write tests", "actor-1", OCCURRED_AT));

        Activity activity = awaitActivity(activityRepository, 1L, TicketCreated.TYPE);

        assertThat(activity.eventId()).isEqualTo("sub-1");
        assertThat(activity.boardId()).isEqualTo(1L);
        assertThat(activity.type()).isEqualTo(TicketCreated.TYPE);
        assertThat(activity.summary()).isEqualTo("Ticket #100 created: Write tests");
        assertThat(activity.actorId()).isEqualTo("actor-1");
        assertThat(activity.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(activity.recordedAt()).isNotNull();
    }

    @Test
    @DisplayName(
            """
            Given a TicketMoved event on the topic, \
            when the subscription reads it, \
            then the activity is recorded with a move summary\
            """)
    void readsTicketMovedEvent() {
        publisher.publish("sub-2", new TicketMoved(101L, 2L, 10L, 20L, "Fix login", "Doing", "assignee-9", "actor-2",
                OCCURRED_AT));

        Activity activity = awaitActivity(activityRepository, 2L, TicketMoved.TYPE);

        assertThat(activity.eventId()).isEqualTo("sub-2");
        assertThat(activity.boardId()).isEqualTo(2L);
        assertThat(activity.summary()).isEqualTo("Ticket \"Fix login\" moved to Doing");
        assertThat(activity.actorId()).isEqualTo("actor-2");
        assertThat(activity.recipientId()).isEqualTo("assignee-9");
        assertThat(activity.recipientMessage()).isEqualTo("actor-2 moved your ticket \"Fix login\" to Doing");
        assertThat(activity.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName(
            """
            Given a TicketAssigned event on the topic, \
            when the subscription reads it, \
            then the activity is recorded with an assignment summary\
            """)
    void readsTicketAssignedEvent() {
        publisher.publish("sub-3", new TicketAssigned(102L, 3L, "Fix login", "assignee-9", "actor-3", OCCURRED_AT));

        Activity activity = awaitActivity(activityRepository, 3L, TicketAssigned.TYPE);

        assertThat(activity.eventId()).isEqualTo("sub-3");
        assertThat(activity.boardId()).isEqualTo(3L);
        assertThat(activity.summary()).isEqualTo("Ticket \"Fix login\" assigned to assignee-9");
        assertThat(activity.actorId()).isEqualTo("actor-3");
        assertThat(activity.recipientId()).isEqualTo("assignee-9");
        assertThat(activity.recipientMessage()).isEqualTo("actor-3 assigned \"Fix login\" to you");
        assertThat(activity.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName(
            """
            Given a TicketCommented event on the topic, \
            when the subscription reads it, \
            then the activity is recorded and addressed to the ticket's assignee\
            """)
    void readsTicketCommentedEvent() {
        publisher.publish("sub-4",
                new TicketCommented(103L, 4L, 500L, "Fix login", "assignee-9", "actor-4", OCCURRED_AT));

        Activity activity = awaitActivity(activityRepository, 4L, TicketCommented.TYPE);

        assertThat(activity.eventId()).isEqualTo("sub-4");
        assertThat(activity.boardId()).isEqualTo(4L);
        assertThat(activity.summary()).isEqualTo("Ticket \"Fix login\" commented on by actor-4");
        assertThat(activity.actorId()).isEqualTo("actor-4");
        assertThat(activity.recipientId()).isEqualTo("assignee-9");
        assertThat(activity.recipientMessage()).isEqualTo("actor-4 commented on your ticket \"Fix login\"");
        assertThat(activity.occurredAt()).isEqualTo(OCCURRED_AT);
    }
}
