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
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView.SwimlaneView;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView.TicketView;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.CreateBoard;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.CreateSwimlane;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.CreateTicket;
import org.unibl.etf.pisio.boardservice.domain.Board;
import org.unibl.etf.pisio.boardservice.domain.Ticket;
import org.unibl.etf.pisio.boardservice.integration.ServiceBusTestSupportConfig.BoardEventTestReceiver;
import org.unibl.etf.pisio.boardservice.repository.BoardRepository;
import org.unibl.etf.pisio.boardservice.repository.TicketRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.unibl.etf.pisio.boardservice.integration.BoardIntegrationTestSupport.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, ServiceBusTestSupportConfig.class})
class BoardControllerIntegrationTest {

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private BoardEventTestReceiver eventReceiver;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Given a valid request, when POST /boards is called, then a 201 response is returned and the board is persisted")
    void createBoardEndpoint() {
        BoardView response = restTestClient.post()
                .uri("/boards")
                .body(new CreateBoard("Sprint board"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "/boards/\\d+")
                .expectBody(BoardView.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Sprint board");
        assertThat(response.swimlanes()).isEmpty();

        Board persisted = boardRepository.findById(response.id()).orElseThrow();
        assertThat(persisted.name()).isEqualTo("Sprint board");
    }

    @Test
    @DisplayName("Given a valid request, when POST /boards/{boardId}/swimlanes is called, then a 201 response is returned and the swimlane is persisted on the board")
    void addSwimlaneEndpoint() {
        Long boardId = createBoard(restTestClient, "Board");

        SwimlaneView response = restTestClient.post()
                .uri("/boards/{boardId}/swimlanes", boardId)
                .body(new CreateSwimlane("To Do"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().location("/boards/" + boardId)
                .expectBody(SwimlaneView.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("To Do");

        Board persisted = boardRepository.findById(boardId).orElseThrow();
        assertThat(persisted.swimlanes()).hasSize(1);
        assertThat(persisted.swimlanes().getFirst().id()).isEqualTo(response.id());
        assertThat(persisted.swimlanes().getFirst().title()).isEqualTo("To Do");
    }

    @Test
    @DisplayName("Given a valid request, when POST /boards/{boardId}/tickets is called, then a 201 response is returned, the ticket is persisted and a TicketCreated event is published")
    void createTicketEndpoint() throws Exception {
        Long boardId = createBoard(restTestClient, "Board");
        Long swimlaneId = addSwimlane(restTestClient, boardId, "To Do");

        TicketView response = restTestClient.post()
                .uri("/boards/{boardId}/tickets", boardId)
                .body(new CreateTicket(swimlaneId, "Write tests", "Cover the happy paths"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "/tickets/\\d+")
                .expectBody(TicketView.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Write tests");
        assertThat(response.description()).isEqualTo("Cover the happy paths");
        assertThat(response.assigneeId()).isNull();
        assertThat(response.position()).isZero();

        Ticket persisted = ticketRepository.findById(response.id()).orElseThrow();
        assertThat(persisted.boardId()).isEqualTo(boardId);
        assertThat(persisted.swimlaneId()).isEqualTo(swimlaneId);
        assertThat(persisted.title()).isEqualTo("Write tests");

        ServiceBusReceivedMessage event = awaitEvent(eventReceiver, "TicketCreated", response.id());
        JsonNode payload = objectMapper.readTree(event.getBody().toString());
        assertThat(payload.get("ticketId").asLong()).isEqualTo(response.id());
        assertThat(payload.get("boardId").asLong()).isEqualTo(boardId);
        assertThat(payload.get("swimlaneId").asLong()).isEqualTo(swimlaneId);
        assertThat(payload.get("title").asText()).isEqualTo("Write tests");
        assertThat(payload.get("actorId").asText()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("Given an existing board with a swimlane and a ticket, when GET /boards/{boardId} is called, then the full board view is returned")
    void getBoardEndpoint() {
        Long boardId = createBoard(restTestClient, "Board");
        Long swimlaneId = addSwimlane(restTestClient, boardId, "To Do");
        Long ticketId = createTicket(restTestClient, boardId, swimlaneId, "Write tests", "Cover the happy paths");

        BoardView response = restTestClient.get()
                .uri("/boards/{boardId}", boardId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(BoardView.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(boardId);
        assertThat(response.name()).isEqualTo("Board");
        assertThat(response.swimlanes()).hasSize(1);

        SwimlaneView swimlane = response.swimlanes().getFirst();
        assertThat(swimlane.id()).isEqualTo(swimlaneId);
        assertThat(swimlane.title()).isEqualTo("To Do");
        assertThat(swimlane.tickets()).hasSize(1);

        TicketView ticket = swimlane.tickets().getFirst();
        assertThat(ticket.id()).isEqualTo(ticketId);
        assertThat(ticket.title()).isEqualTo("Write tests");
        assertThat(ticket.description()).isEqualTo("Cover the happy paths");
    }
}
