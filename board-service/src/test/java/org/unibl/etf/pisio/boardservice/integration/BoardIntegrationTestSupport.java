package org.unibl.etf.pisio.boardservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView.SwimlaneView;
import org.unibl.etf.pisio.boardservice.controller.dto.BoardView.TicketView;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.CreateBoard;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.CreateSwimlane;
import org.unibl.etf.pisio.boardservice.controller.dto.Requests.CreateTicket;
import org.unibl.etf.pisio.boardservice.integration.ServiceBusTestSupportConfig.BoardEventTestReceiver;

final class BoardIntegrationTestSupport {

    private BoardIntegrationTestSupport() {
    }

    @SuppressWarnings("SameParameterValue")
    static Long createBoard(RestTestClient client, String name) {
        BoardView body = client.post()
                .uri("/boards")
                .body(new CreateBoard(name))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(BoardView.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).isNotNull();
        return body.id();
    }

    static Long addSwimlane(RestTestClient client, Long boardId, String title) {
        SwimlaneView body = client.post()
                .uri("/boards/{boardId}/swimlanes", boardId)
                .body(new CreateSwimlane(title))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(SwimlaneView.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).isNotNull();
        return body.id();
    }

    @SuppressWarnings("SameParameterValue")
    static Long createTicket(RestTestClient client, Long boardId, Long swimlaneId, String title, String description) {
        TicketView body = client.post()
                .uri("/boards/{boardId}/tickets", boardId)
                .body(new CreateTicket(swimlaneId, title, description))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TicketView.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).isNotNull();
        return body.id();
    }

    static ServiceBusReceivedMessage awaitEvent(BoardEventTestReceiver receiver, String eventType, Long aggregateId) {
        AtomicReference<ServiceBusReceivedMessage> found = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            Optional<ServiceBusReceivedMessage> match = receiver.received().stream()
                    .filter(m -> eventType.equals(m.getSubject()))
                    .filter(m -> String.valueOf(aggregateId).equals(m.getApplicationProperties().get("aggregateId")))
                    .findFirst();
            assertThat(match).isPresent();
            found.set(match.get());
        });
        return found.get();
    }
}
