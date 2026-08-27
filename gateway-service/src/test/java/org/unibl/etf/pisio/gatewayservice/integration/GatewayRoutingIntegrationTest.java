package org.unibl.etf.pisio.gatewayservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unibl.etf.pisio.gatewayservice.GatewayTestSupport;
import reactor.core.publisher.Mono;

/**
 * Proves the gateway works as the SPA's single origin: what the browser asks for arrives at the
 * right backend, without the api prefix and with a Bearer token attached (ADR 0005). Only the token
 * source is stubbed — obtaining a real one needs a live authorization server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "trackly.gateway.serve-spa=false")
@AutoConfigureWebTestClient
@Import(PreAuthenticatedSecurity.class)
class GatewayRoutingIntegrationTest extends GatewayTestSupport {

    private static final String ACCESS_TOKEN = "relayed-access-token";

    private static final RecordingBackend BOARD = new RecordingBackend("boards");

    private static final RecordingBackend NOTIFICATION = new RecordingBackend("activity");

    private static final RecordingBackend FRONTEND = new RecordingBackend("<html>dev server</html>");

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ReactiveOAuth2AuthorizedClientManager authorizedClientManager;

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry registry) {
        registry.add("trackly.gateway.backends.board.uri", BOARD::uri);
        registry.add("trackly.gateway.backends.notification.uri", NOTIFICATION::uri);
        registry.add("trackly.gateway.frontend-uri", FRONTEND::uri);
    }

    @AfterAll
    static void stopBackends() {
        BOARD.close();
        NOTIFICATION.close();
        FRONTEND.close();
    }

    @BeforeEach
    void authorizeTheGatewayClient() {
        when(authorizedClientManager.authorize(any()))
                .thenReturn(Mono.just(PreAuthenticatedSecurity.authorizedClient(ACCESS_TOKEN)));
    }

    @Test
    @DisplayName(
            """
            Given a board path under the api prefix, \
            when it is requested, \
            then board-service receives it without the prefix and with the relayed token\
            """)
    void routesBoardPathsToBoardServiceWithoutThePrefix() {
        webTestClient.get().uri("/api/boards/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("boards");

        RecordingBackend.ReceivedRequest received = BOARD.takeRequest();
        assertThat(received.path()).isEqualTo("/boards/1");
        assertThat(received.authorization()).isEqualTo("Bearer " + ACCESS_TOKEN);
    }

    @Test
    @DisplayName("Given a ticket path under the api prefix, when it is requested, then it reaches board-service too")
    void routesTicketPathsToBoardService() {
        webTestClient.get().uri("/api/tickets/7")
                .exchange()
                .expectStatus().isOk();

        assertThat(BOARD.takeRequest().path()).isEqualTo("/tickets/7");
    }

    @Test
    @DisplayName(
            """
            Given an activity path, \
            when it is requested, \
            then it reaches notification-service rather than board-service\
            """)
    void routesActivityPathsToNotificationService() {
        webTestClient.get().uri("/api/activity")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("activity");

        assertThat(NOTIFICATION.takeRequest().path()).isEqualTo("/activity");
        assertThat(BOARD.takeRequest()).isNull();
    }

    @Test
    @DisplayName(
            """
            Given the SPA is proxied to the dev server, \
            when a page is requested, \
            then the dev server serves it on the gateway's origin\
            """)
    void proxiesEverythingElseToTheDevServer() {
        webTestClient.get().uri("/board/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("<html>dev server</html>");

        assertThat(FRONTEND.takeRequest().path()).isEqualTo("/board/1");
    }
}
