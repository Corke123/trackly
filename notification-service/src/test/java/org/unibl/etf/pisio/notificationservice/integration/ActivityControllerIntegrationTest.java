package org.unibl.etf.pisio.notificationservice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.unibl.etf.pisio.notificationservice.controller.ActivityController.ActivityView;
import org.unibl.etf.pisio.notificationservice.domain.Activity;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketAssigned;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketCreated;
import org.unibl.etf.pisio.notificationservice.domain.event.TicketMoved;
import org.unibl.etf.pisio.notificationservice.repository.ActivityRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, ServiceBusTestSupportConfig.class})
class ActivityControllerIntegrationTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-25T10:00:00Z");
    private static final ParameterizedTypeReference<List<ActivityView>> FEED = new ParameterizedTypeReference<>() {
    };

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private ActivityRepository activityRepository;

    @BeforeEach
    void resetFeed() {
        activityRepository.deleteAll();
    }

    @Test
    @DisplayName("Given recorded activity on several boards, when GET /activity is called, then the whole feed is returned newest first")
    void feedAcrossBoards() {
        Activity older = save(10L, TicketCreated.TYPE, "Ticket #100 created: Write tests", "actor-1", OCCURRED_AT.minusSeconds(60));
        Activity newer = save(20L, TicketMoved.TYPE, "Ticket #101 moved to swimlane 20", "actor-2", OCCURRED_AT);

        List<ActivityView> response = restTestClient.get()
                .uri("/activity")
                .exchange()
                .expectStatus().isOk()
                .expectBody(FEED)
                .returnResult()
                .getResponseBody();

        assertThat(response).containsExactly(
                new ActivityView(newer.id(), 20L, TicketMoved.TYPE, "Ticket #101 moved to swimlane 20", "actor-2", OCCURRED_AT),
                new ActivityView(older.id(), 10L, TicketCreated.TYPE, "Ticket #100 created: Write tests", "actor-1", OCCURRED_AT.minusSeconds(60))
        );
    }

    @Test
    @DisplayName("Given recorded activity on several boards, when GET /activity?boardId is called, then only that board's feed is returned newest first")
    void feedForSingleBoard() {
        Activity older = save(10L, TicketCreated.TYPE, "Ticket #100 created: Write tests", "actor-1", OCCURRED_AT.minusSeconds(60));
        Activity newer = save(10L, TicketAssigned.TYPE, "Ticket #100 assigned to assignee-9", "actor-1", OCCURRED_AT);
        save(20L, TicketMoved.TYPE, "Ticket #101 moved to swimlane 20", "actor-2", OCCURRED_AT);

        List<ActivityView> response = restTestClient.get()
                .uri("/activity?boardId={boardId}", 10L)
                .exchange()
                .expectStatus().isOk()
                .expectBody(FEED)
                .returnResult()
                .getResponseBody();

        assertThat(response).containsExactly(
                new ActivityView(newer.id(), 10L, TicketAssigned.TYPE, "Ticket #100 assigned to assignee-9", "actor-1", OCCURRED_AT),
                new ActivityView(older.id(), 10L, TicketCreated.TYPE, "Ticket #100 created: Write tests", "actor-1", OCCURRED_AT.minusSeconds(60))
        );
    }

    @Test
    @DisplayName("Given no recorded activity, when GET /activity is called, then an empty feed is returned")
    void emptyFeed() {
        List<ActivityView> response = restTestClient.get()
                .uri("/activity")
                .exchange()
                .expectStatus().isOk()
                .expectBody(FEED)
                .returnResult()
                .getResponseBody();

        assertThat(response).isEmpty();
    }

    private Activity save(Long boardId, String type, String summary, String actorId, Instant occurredAt) {
        return activityRepository.save(new Activity("event-" + boardId + "-" + type, boardId, type, summary,
                actorId, null, null, occurredAt));
    }
}
