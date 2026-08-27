package org.unibl.etf.pisio.boardservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.unibl.etf.pisio.boardservice.integration.BoardIntegrationTestSupport.addSwimlane;
import static org.unibl.etf.pisio.boardservice.integration.BoardIntegrationTestSupport.awaitEvent;
import static org.unibl.etf.pisio.boardservice.integration.BoardIntegrationTestSupport.createBoard;
import static org.unibl.etf.pisio.boardservice.integration.BoardIntegrationTestSupport.createTicket;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView.TicketView;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.UpdateTicket;
import org.unibl.etf.pisio.boardservice.domain.Ticket;
import org.unibl.etf.pisio.boardservice.integration.ServiceBusTestSupportConfig.BoardEventTestReceiver;
import org.unibl.etf.pisio.boardservice.repository.TicketRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, ServiceBusTestSupportConfig.class})
@ActiveProfiles("local")
class TicketControllerIntegrationTest {

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    @Qualifier("userRestTestClient")
    private RestTestClient userRestTestClient;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private BoardEventTestReceiver eventReceiver;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Given an existing ticket, when GET /tickets/{ticketId} is called, then the ticket view is returned")
    void getTicketEndpoint() {
        Long boardId = createBoard(restTestClient, "Board");
        Long swimlaneId = addSwimlane(restTestClient, boardId, "To Do");
        Long ticketId = createTicket(restTestClient, boardId, swimlaneId, "Write tests", "Cover the happy paths");

        TicketView response = restTestClient.get()
                .uri("/tickets/{ticketId}", ticketId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TicketView.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(ticketId);
        assertThat(response.title()).isEqualTo("Write tests");
        assertThat(response.description()).isEqualTo("Cover the happy paths");
        assertThat(response.assigneeId()).isNull();
        assertThat(response.position()).isZero();
    }

    @Test
    @DisplayName(
            """
            Given swimlaneId and position, \
            when PATCH /tickets/{ticketId} is called, \
            then the ticket is moved, persisted and a TicketMoved event is published\
            """)
    void moveTicketEndpoint() throws Exception {
        Long boardId = createBoard(restTestClient, "Board");
        Long fromSwimlaneId = addSwimlane(restTestClient, boardId, "To Do");
        Long toSwimlaneId = addSwimlane(restTestClient, boardId, "Doing");
        Long ticketId = createTicket(restTestClient, boardId, fromSwimlaneId, "Write tests", "Cover the happy paths");

        TicketView response = restTestClient.patch()
                .uri("/tickets/{ticketId}", ticketId)
                .body(new UpdateTicket(toSwimlaneId, 0, null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TicketView.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.position()).isZero();

        Ticket persisted = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(persisted.swimlaneId()).isEqualTo(toSwimlaneId);
        assertThat(persisted.position()).isZero();

        ServiceBusReceivedMessage event = awaitEvent(eventReceiver, "TicketMoved", ticketId);
        JsonNode payload = objectMapper.readTree(event.getBody().toString());
        assertThat(payload.get("ticketId").asLong()).isEqualTo(ticketId);
        assertThat(payload.get("fromSwimlaneId").asLong()).isEqualTo(fromSwimlaneId);
        assertThat(payload.get("toSwimlaneId").asLong()).isEqualTo(toSwimlaneId);
        assertThat(payload.get("title").asText()).isEqualTo("Write tests");
        assertThat(payload.get("toSwimlaneTitle").asText()).isEqualTo("Doing");
        assertThat(payload.get("assigneeId").isNull()).isTrue();
        assertThat(payload.get("actorId").asText()).isEqualTo("admin");
    }

    @Test
    @DisplayName(
            """
            Given an assigneeId, \
            when PATCH /tickets/{ticketId} is called, \
            then the ticket is assigned, persisted and a TicketAssigned event is published\
            """)
    void assignTicketEndpoint() throws Exception {
        Long boardId = createBoard(restTestClient, "Board");
        Long swimlaneId = addSwimlane(restTestClient, boardId, "To Do");
        Long ticketId = createTicket(restTestClient, boardId, swimlaneId, "Write tests", "Cover the happy paths");

        TicketView response = restTestClient.patch()
                .uri("/tickets/{ticketId}", ticketId)
                .body(new UpdateTicket(null, null, "user-1"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TicketView.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.assigneeId()).isEqualTo("user-1");

        Ticket persisted = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(persisted.assigneeId()).isEqualTo("user-1");

        ServiceBusReceivedMessage event = awaitEvent(eventReceiver, "TicketAssigned", ticketId);
        JsonNode payload = objectMapper.readTree(event.getBody().toString());
        assertThat(payload.get("ticketId").asLong()).isEqualTo(ticketId);
        assertThat(payload.get("title").asText()).isEqualTo("Write tests");
        assertThat(payload.get("assigneeId").asText()).isEqualTo("user-1");
        assertThat(payload.get("actorId").asText()).isEqualTo("admin");
    }

    @Test
    @DisplayName(
            """
            Given a ticket dragged out of the middle of a swimlane, \
            when PATCH /tickets/{ticketId} is called, \
            then both swimlanes are left densely numbered from zero\
            """)
    void moveTicketRenumbersBothSwimlanes() {
        Long boardId = createBoard(restTestClient, "Board");
        Long fromSwimlaneId = addSwimlane(restTestClient, boardId, "To Do");
        Long toSwimlaneId = addSwimlane(restTestClient, boardId, "Doing");
        Long first = createTicket(restTestClient, boardId, fromSwimlaneId, "First", null);
        Long second = createTicket(restTestClient, boardId, fromSwimlaneId, "Second", null);
        Long third = createTicket(restTestClient, boardId, fromSwimlaneId, "Third", null);
        Long alreadyThere = createTicket(restTestClient, boardId, toSwimlaneId, "Already there", null);

        restTestClient.patch()
                .uri("/tickets/{ticketId}", second)
                .body(new UpdateTicket(toSwimlaneId, 0, null))
                .exchange()
                .expectStatus().isOk();

        assertThat(ticketRepository.findBySwimlaneIdOrderByPositionAsc(fromSwimlaneId))
                .extracting(Ticket::id, Ticket::position)
                .containsExactly(tuple(first, 0), tuple(third, 1));
        assertThat(ticketRepository.findBySwimlaneIdOrderByPositionAsc(toSwimlaneId))
                .extracting(Ticket::id, Ticket::position)
                .containsExactly(tuple(second, 0), tuple(alreadyThere, 1));
    }

    @Test
    @DisplayName(
            """
            Given an admin and a ticket in the middle of a swimlane, \
            when DELETE /tickets/{ticketId} is called, then the ticket is gone, \
            the lane stays densely numbered and a TicketDeleted event is published\
            """)
    void deleteTicketEndpoint() throws Exception {
        Long boardId = createBoard(restTestClient, "Board");
        Long swimlaneId = addSwimlane(restTestClient, boardId, "To Do");
        Long first = createTicket(restTestClient, boardId, swimlaneId, "First", null);
        Long doomed = createTicket(restTestClient, boardId, swimlaneId, "Doomed", null);
        Long third = createTicket(restTestClient, boardId, swimlaneId, "Third", null);

        restTestClient.delete()
                .uri("/tickets/{ticketId}", doomed)
                .exchange()
                .expectStatus().isNoContent();

        assertThat(ticketRepository.findById(doomed)).isEmpty();
        assertThat(ticketRepository.findBySwimlaneIdOrderByPositionAsc(swimlaneId))
                .extracting(Ticket::id, Ticket::position)
                .containsExactly(tuple(first, 0), tuple(third, 1));

        ServiceBusReceivedMessage event = awaitEvent(eventReceiver, "TicketDeleted", doomed);
        JsonNode payload = objectMapper.readTree(event.getBody().toString());
        assertThat(payload.get("ticketId").asLong()).isEqualTo(doomed);
        assertThat(payload.get("title").asText()).isEqualTo("Doomed");
        assertThat(payload.get("swimlaneTitle").asText()).isEqualTo("To Do");
        assertThat(payload.get("actorId").asText()).isEqualTo("admin");
    }

    @Test
    @DisplayName(
            """
            Given a plain user, \
            when DELETE /tickets/{ticketId} is called, \
            then it is forbidden and the ticket survives\
            """)
    void plainUsersMayNotDeleteTickets() {
        Long boardId = createBoard(restTestClient, "Board");
        Long swimlaneId = addSwimlane(restTestClient, boardId, "To Do");
        Long ticketId = createTicket(restTestClient, boardId, swimlaneId, "Not yours to delete", null);

        userRestTestClient.delete()
                .uri("/tickets/{ticketId}", ticketId)
                .exchange()
                .expectStatus().isForbidden();

        assertThat(ticketRepository.findById(ticketId)).isPresent();
    }

    @Test
    @DisplayName(
            """
            Given a ticket reordered inside its swimlane, \
            when PATCH /tickets/{ticketId} is called, \
            then the remaining tickets shift to keep a dense order\
            """)
    void moveTicketReordersWithinSwimlane() {
        Long boardId = createBoard(restTestClient, "Board");
        Long swimlaneId = addSwimlane(restTestClient, boardId, "To Do");
        Long first = createTicket(restTestClient, boardId, swimlaneId, "First", null);
        Long second = createTicket(restTestClient, boardId, swimlaneId, "Second", null);
        Long third = createTicket(restTestClient, boardId, swimlaneId, "Third", null);

        restTestClient.patch()
                .uri("/tickets/{ticketId}", third)
                .body(new UpdateTicket(swimlaneId, 0, null))
                .exchange()
                .expectStatus().isOk();

        assertThat(ticketRepository.findBySwimlaneIdOrderByPositionAsc(swimlaneId))
                .extracting(Ticket::id, Ticket::position)
                .containsExactly(tuple(third, 0), tuple(first, 1), tuple(second, 2));
    }
}
