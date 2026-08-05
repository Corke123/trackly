package org.unibl.etf.pisio.identityservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
public class TokenCustomizerConfig {

    private static final String ROLE_PREFIX = "ROLE_";

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
                    || OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
                Authentication principal = context.getPrincipal();
                if (principal != null) {
                    List<String> roles = principal.getAuthorities()
                            .stream()
                            .map(GrantedAuthority::getAuthority)
                            .filter(Objects::nonNull)
                            .filter(authority -> authority.startsWith(ROLE_PREFIX))
                            .collect(Collectors.toCollection(ArrayList::new));
                    context.getClaims().claim("roles", roles);
                    context.getClaims().claim("preferred_username", principal.getName());
                }
            }
        };
    }
}
