package org.unibl.etf.pisio.gatewayservice.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unibl.etf.pisio.gatewayservice.GatewayTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The production mode of ADR 0006: the SPA is part of this image. A deep link has to render the SPA
 * shell so a refresh does not 404, while the assets that shell then asks for must be served as
 * themselves.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "trackly.gateway.serve-spa=true")
@AutoConfigureWebTestClient
@Import(PreAuthenticatedSecurity.class)
class BundledSpaIntegrationTest extends GatewayTestSupport {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Given a deep link into the SPA, when the browser navigates to it, then the SPA shell is served so the Angular router can take over")
    void servesTheSpaShellForADeepLink() {
        webTestClient.get().uri("/board/1")
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body)
                        .contains("<app-root></app-root>"));
    }

    @Test
    @DisplayName("Given the SPA asks for one of its bundles, when it is requested, then the bundle is served rather than the SPA shell")
    void servesAssetsAsThemselves() {
        webTestClient.get().uri("/main-test.js")
                .header(HttpHeaders.ACCEPT, "*/*")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body)
                        .contains("console.log('trackly')"));
    }
}
