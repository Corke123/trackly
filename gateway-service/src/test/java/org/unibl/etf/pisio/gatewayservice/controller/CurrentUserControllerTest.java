package org.unibl.etf.pisio.gatewayservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unibl.etf.pisio.gatewayservice.GatewayTestSupport;
import org.unibl.etf.pisio.gatewayservice.controller.CurrentUserController.CurrentUser;

@SpringBootTest
@AutoConfigureWebTestClient
class CurrentUserControllerTest extends GatewayTestSupport {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName(
            """
            Given a signed-in admin, \
            when the current user is requested, \
            then the username and the admin flag are returned\
            """)
    void reportsAnAdmin() {
        CurrentUser response = webTestClient.mutateWith(mockOidcLogin().idToken(idTokenFor("admin", "ROLE_ADMIN")))
                .get().uri("/api/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CurrentUser.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isEqualTo(new CurrentUser("admin", List.of("ROLE_ADMIN"), true));
    }

    @Test
    @DisplayName(
            """
            Given a signed-in user without the admin role, \
            when the current user is requested, \
            then the admin flag is false\
            """)
    void reportsPlainUser() {
        CurrentUser response = webTestClient.mutateWith(mockOidcLogin().idToken(idTokenFor("demo", "ROLE_USER")))
                .get().uri("/api/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CurrentUser.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isEqualTo(new CurrentUser("demo", List.of("ROLE_USER"), false));
    }

    @Test
    @DisplayName(
            """
            Given an id token carrying no roles, \
            when the current user is requested, \
            then no roles are reported rather than failing\
            """)
    void toleratesAnIdTokenWithoutRoles() {
        CurrentUser response = webTestClient.mutateWith(mockOidcLogin()
                        .idToken(builder -> builder.subject("demo")
                                .claims(claims -> claims.remove("roles"))))
                .get().uri("/api/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CurrentUser.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isEqualTo(new CurrentUser("demo", List.of(), false));
    }

    private static Consumer<OidcIdToken.Builder> idTokenFor(String username, String role) {
        return builder -> builder
                .subject(username)
                .claim("preferred_username", username)
                .claim("roles", List.of(role));
    }
}
