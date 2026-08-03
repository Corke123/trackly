package org.unibl.etf.pisio.gatewayservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;
import org.unibl.etf.pisio.gatewayservice.GatewayTestSupport;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;

@SpringBootTest
@AutoConfigureWebTestClient
class SecurityConfigTest extends GatewayTestSupport {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveOAuth2AuthorizedClientManager authorizedClientManager;

    @Autowired
    private WebFilter csrfCookieWebFilter;

    @Test
    @DisplayName("Given no session, when the SPA is requested, then the browser is sent to the authorization server to log in")
    void unauthenticatedNavigationStartsTheLogin() {
        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, "/oauth2/authorization/trackly");
    }

    @Test
    @DisplayName("Given no session, when an api path is requested, then it is not proxied but sent to log in first")
    void unauthenticatedApiCallStartsTheLogin() {
        webTestClient.get().uri("/api/boards/1")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, "/oauth2/authorization/trackly");
    }

    @Test
    @DisplayName("Given no session, when the readiness probe is requested, then it answers so the platform can see the gateway is up")
    void healthProbeIsPublic() {
        webTestClient.get().uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Given a logged-in session, when a state-changing request arrives without the CSRF token, then it is rejected")
    void rejectsAStateChangingRequestWithoutTheCsrfToken() {
        webTestClient.mutateWith(mockOidcLogin())
                .post().uri("/api/tickets")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("Given the SPA needs to read the CSRF token, when a page is served, then the token is written to a cookie it can read")
    void publishesTheCsrfTokenAsAReadableCookie() {
        webTestClient.mutateWith(mockOidcLogin())
                .get().uri("/actuator/info")
                .exchange()
                .expectCookie().exists("XSRF-TOKEN")
                .expectCookie().httpOnly("XSRF-TOKEN", false);
    }

    @Test
    @DisplayName("Given a request that carries no CSRF token attribute, when the cookie filter runs, then the response is still completed")
    void csrfCookieFilterToleratesAMissingToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());

        StepVerifier.create(csrfCookieWebFilter.filter(exchange, e -> e.getResponse().setComplete()))
                .verifyComplete();

        assertThat(exchange.getResponse().getCookies().get("XSRF-TOKEN")).isNull();
    }

    @Test
    @DisplayName("Given the token relay filter needs an access token, when the context is started, then a client manager that can refresh one is available")
    void publishesAnAuthorizedClientManagerThatCanRefresh() {
        assertThat(authorizedClientManager).isInstanceOf(DefaultReactiveOAuth2AuthorizedClientManager.class);
    }

    @Test
    @DisplayName("Given a logged-in session, when the user logs out, then the browser is redirected away rather than left signed in")
    void logoutRedirects() {
        webTestClient.mutateWith(mockOidcLogin()).mutateWith(csrf())
                .post().uri("/logout")
                .exchange()
                .expectStatus().isFound();
    }

    /**
     * Echoing the cookie verbatim is the whole of Angular's CSRF handling, and it is not what the
     * framework accepts by default — the default masks the value it publishes, so the SPA's every
     * write would come back 403. Exercised without the test mutators for that reason: they would
     * supply a token the way the framework prefers rather than the way the client actually sends it.
     */
    @Test
    @DisplayName("Given the CSRF token from the cookie, when the SPA echoes it back in the header, then the request is accepted")
    void acceptsTheCsrfTokenTheWayTheSpaSendsIt() {
        String csrfToken = webTestClient.mutateWith(mockOidcLogin())
                .get().uri("/actuator/info")
                .exchange()
                .expectCookie().exists("XSRF-TOKEN")
                .returnResult(Void.class)
                .getResponseCookies()
                .getFirst("XSRF-TOKEN")
                .getValue();

        webTestClient.mutateWith(mockOidcLogin())
                .post().uri("/logout")
                .cookie("XSRF-TOKEN", csrfToken)
                .header("X-XSRF-TOKEN", csrfToken)
                .exchange()
                .expectStatus().isFound();
    }
}
