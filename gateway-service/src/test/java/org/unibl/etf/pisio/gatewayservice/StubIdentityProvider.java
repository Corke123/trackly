package org.unibl.etf.pisio.gatewayservice;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

public final class StubIdentityProvider {

    public static final String END_SESSION_ENDPOINT = "/connect/logout";

    private final String issuer;

    public StubIdentityProvider() {
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes.get("/.well-known/openid-configuration", (_, response) -> response
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .sendString(Mono.fromSupplier(this::discoveryDocument))))
                .bindNow();
        this.issuer = "http://localhost:" + server.port();
    }

    public String issuer() {
        return issuer;
    }

    private String discoveryDocument() {
        return
                """
                {
                  "issuer": "%1$s",
                  "authorization_endpoint": "%1$s/oauth2/authorize",
                  "token_endpoint": "%1$s/oauth2/token",
                  "jwks_uri": "%1$s/oauth2/jwks",
                  "userinfo_endpoint": "%1$s/userinfo",
                  "end_session_endpoint": "%1$s%2$s",
                  "response_types_supported": ["code"],
                  "subject_types_supported": ["public"],
                  "id_token_signing_alg_values_supported": ["RS256"],
                  "scopes_supported": ["openid", "profile", "email"],
                  "grant_types_supported": ["authorization_code", "refresh_token"],
                  "token_endpoint_auth_methods_supported": ["client_secret_basic"],
                  "code_challenge_methods_supported": ["S256"]
                }
                """.formatted(issuer, END_SESSION_ENDPOINT);
    }
}
