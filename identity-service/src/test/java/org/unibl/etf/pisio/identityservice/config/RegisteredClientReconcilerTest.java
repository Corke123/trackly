package org.unibl.etf.pisio.identityservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegisteredClientReconcilerTest {

    private static final String GATEWAY = "https://gateway-staging.azurecontainerapps.io";
    private static final String REDIRECT_URI = GATEWAY + "/login/oauth2/code/trackly";

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final RecordingRepository repository = new RecordingRepository();

    @Test
    @DisplayName("Given a row seeded with a stale gateway URL, when reconciling, then both URIs are corrected")
    void convergesStaleUris() {
        repository.seed(client("http://localhost:8080/login/oauth2/code/trackly", "http://localhost:8080",
                encoder.encode("s3cret")));

        reconcile("s3cret");

        RegisteredClient saved = repository.saved().getFirst();
        assertThat(saved.getRedirectUris()).containsExactly(REDIRECT_URI);
        assertThat(saved.getPostLogoutRedirectUris()).containsExactly(GATEWAY);
    }

    @Test
    @DisplayName("Given a plaintext secret, when reconciling, then it is stored hashed and verifiable")
    void hashesPlaintextSecret() {
        repository.seed(client(REDIRECT_URI, GATEWAY, "not-a-valid-hash"));

        reconcile("s3cret");

        String stored = repository.saved().getFirst().getClientSecret();
        assertThat(stored).isNotEqualTo("s3cret").startsWith("{bcrypt}");
        assertThat(encoder.matches("s3cret", stored)).isTrue();
    }

    @Test
    @DisplayName("Given a row that already agrees, when reconciling, then nothing is written")
    void noWriteWhenConverged() {
        repository.seed(client(REDIRECT_URI, GATEWAY, encoder.encode("s3cret")));

        reconcile("s3cret");

        assertThat(repository.saved()).isEmpty();
    }

    @Test
    @DisplayName("Given a pre-encoded secret, when reconciling, then it is stored verbatim rather than double-encoded")
    void preEncodedSecretPassesThrough() {
        repository.seed(client("http://stale", GATEWAY, "{noop}s3cret"));

        reconcile("{noop}s3cret");

        assertThat(repository.saved().getFirst().getClientSecret()).isEqualTo("{noop}s3cret");
    }

    @Test
    @DisplayName("Given no seeded client, when reconciling, then it does not fail")
    void missingClientIsTolerated() {
        reconcile("s3cret");

        assertThat(repository.saved()).isEmpty();
    }

    private void reconcile(String secret) {
        new RegisteredClientReconciler(
                repository, new RegisteredClientProperties("trackly", secret, REDIRECT_URI, GATEWAY))
                .run(null);
    }

    private static RegisteredClient client(String redirectUri, String postLogoutRedirectUri, String secret) {
        return RegisteredClient.withId("id")
                .clientId("trackly")
                .clientSecret(secret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .postLogoutRedirectUri(postLogoutRedirectUri)
                .scope("openid")
                .build();
    }

    private static final class RecordingRepository implements RegisteredClientRepository {

        private RegisteredClient seeded;
        private final List<RegisteredClient> saved = new ArrayList<>();

        void seed(RegisteredClient client) {
            this.seeded = client;
        }

        List<RegisteredClient> saved() {
            return saved;
        }

        @Override
        public void save(RegisteredClient registeredClient) {
            saved.add(registeredClient);
        }

        @Override
        public RegisteredClient findById(String id) {
            return seeded != null && Set.of(seeded.getId()).contains(id) ? seeded : null;
        }

        @Override
        public RegisteredClient findByClientId(String clientId) {
            return seeded != null && seeded.getClientId().equals(clientId) ? seeded : null;
        }
    }
}
