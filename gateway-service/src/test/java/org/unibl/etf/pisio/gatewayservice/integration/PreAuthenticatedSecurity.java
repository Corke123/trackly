package org.unibl.etf.pisio.gatewayservice.integration;

import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@NullMarked
@TestConfiguration
class PreAuthenticatedSecurity {

    private static final ClientRegistration REGISTRATION = ClientRegistration.withRegistrationId("trackly")
            .clientId("trackly")
            .clientSecret("trackly-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:8080/login/oauth2/code/trackly")
            .authorizationUri("http://localhost:9000/oauth2/authorize")
            .tokenUri("http://localhost:9000/oauth2/token")
            .build();

    static Authentication authentication() {
        OAuth2User user = new DefaultOAuth2User(AuthorityUtils.createAuthorityList("ROLE_USER"),
                Map.of("sub", "demo"), "sub");

        return new OAuth2AuthenticationToken(user, user.getAuthorities(), REGISTRATION.getRegistrationId());
    }

    static OAuth2AuthorizedClient authorizedClient(String accessToken) {
        return new OAuth2AuthorizedClient(REGISTRATION, "demo",
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, accessToken, Instant.now(),
                        Instant.now().plusSeconds(300)));
    }

    @Bean
    @Order(-1)
    SecurityWebFilterChain preAuthenticatedFilterChain(ServerHttpSecurity http) {
        http.authorizeExchange(authorizeExchangeSpec -> authorizeExchangeSpec.anyExchange().permitAll())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .securityContextRepository(new ServerSecurityContextRepository() {

                    @Override
                    public Mono<Void> save(ServerWebExchange exchange, @Nullable SecurityContext context) {
                        return Mono.empty();
                    }

                    @Override
                    public Mono<SecurityContext> load(ServerWebExchange exchange) {
                        return Mono.just(new SecurityContextImpl(authentication()));
                    }
                });

        return http.build();
    }
}
