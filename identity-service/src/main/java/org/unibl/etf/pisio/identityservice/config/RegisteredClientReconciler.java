package org.unibl.etf.pisio.identityservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Set;

class RegisteredClientReconciler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RegisteredClientReconciler.class);

    private final RegisteredClientRepository registeredClients;
    private final RegisteredClientProperties properties;
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    RegisteredClientReconciler(RegisteredClientRepository registeredClients, RegisteredClientProperties properties) {
        this.registeredClients = registeredClients;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        RegisteredClient existing = registeredClients.findByClientId(properties.id());
        if (existing == null) {
            log.warn("No registered client '{}' to reconcile; the schema seed did not run", properties.id());
            return;
        }

        boolean secretMatches = secretMatches(existing.getClientSecret());
        boolean urisMatch = existing.getRedirectUris().equals(Set.of(properties.redirectUri()))
                && existing.getPostLogoutRedirectUris().equals(Set.of(properties.postLogoutRedirectUri()));
        if (secretMatches && urisMatch) {
            log.debug("Registered client '{}' already matches configuration", properties.id());
            return;
        }

        registeredClients.save(RegisteredClient.from(existing)
                .clientSecret(secretMatches ? existing.getClientSecret() : encode(properties.secret()))
                .redirectUris(uris -> {
                    uris.clear();
                    uris.add(properties.redirectUri());
                })
                .postLogoutRedirectUris(uris -> {
                    uris.clear();
                    uris.add(properties.postLogoutRedirectUri());
                })
                .build());

        log.info("Reconciled registered client '{}': redirect-uri={}, post-logout-redirect-uri={}, secret-rotated={}",
                properties.id(), properties.redirectUri(), properties.postLogoutRedirectUri(), !secretMatches);
    }

    private String encode(String secret) {
        return secret.startsWith("{") ? secret : passwordEncoder.encode(secret);
    }

    private boolean secretMatches(String storedSecret) {
        if (storedSecret == null) {
            return false;
        }
        if (properties.secret().startsWith("{")) {
            return properties.secret().equals(storedSecret);
        }
        try {
            return passwordEncoder.matches(properties.secret(), storedSecret);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
