package org.unibl.etf.pisio.gatewayservice.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
public class CurrentUserController {

    static final String ADMIN_ROLE = "ROLE_ADMIN";

    @GetMapping("${trackly.gateway.api-prefix}/me")
    public Mono<CurrentUser> currentUser(@AuthenticationPrincipal OidcUser user) {
        List<String> roles = user.getClaimAsStringList("roles");
        List<String> safeRoles = (roles == null) ? List.of() : List.copyOf(roles);

        String username = (user.getPreferredUsername() == null) ? user.getSubject() : user.getPreferredUsername();

        return Mono.just(new CurrentUser(username, safeRoles, safeRoles.contains(ADMIN_ROLE)));
    }

    public record CurrentUser(String username, List<String> roles, boolean admin) {

    }
}
