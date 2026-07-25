package org.unibl.etf.pisio.boardservice.integration;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView.TicketView;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.UpdateTicket;
import org.unibl.etf.pisio.boardservice.domain.Ticket;
import org.unibl.etf.pisio.boardservice.integration.ServiceBusTestSupportConfig.BoardEventTestReceiver;
import org.unibl.etf.pisio.boardservice.repository.TicketRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.unibl.etf.pisio.boardservice.integration.BoardIntegrationTestSupport.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, ServiceBusTestSupportConfig.class})
class TicketControllerIntegrationTest {

    @Autowired
    private RestTestClient restTestClient;

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
    @DisplayName("Given swimlaneId and position, when PATCH /tickets/{ticketId} is called, then the ticket is moved, persisted and a TicketMoved event is published")
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
        assertThat(payload.get("actorId").asText()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("Given an assigneeId, when PATCH /tickets/{ticketId} is called, then the ticket is assigned, persisted and a TicketAssigned event is published")
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
        assertThat(payload.get("assigneeId").asText()).isEqualTo("user-1");
        assertThat(payload.get("actorId").asText()).isEqualTo("anonymous");
    }
}
