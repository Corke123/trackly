package org.unibl.etf.pisio.identityservice.jwk;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.*;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class RotatingJwkSourceTest {

    private static final JWKSelector MATCH_ALL = new JWKSelector(new JWKMatcher.Builder().build());

    @Test
    void loadsInitialKeySetEagerlyAndPreservesOrder() {
        RSAKey newest = generateKey();
        RSAKey previous = generateKey();
        SigningKeys signingKeys = mock(SigningKeys.class);
        given(signingKeys.load()).willReturn(List.of(newest, previous));

        RotatingJwkSource source = new RotatingJwkSource(signingKeys);

        List<JWK> selected = source.get(MATCH_ALL, null);
        assertThat(selected).extracting(JWK::getKeyID).containsExactly(newest.getKeyID(), previous.getKeyID());
    }

    @Test
    void constructorFailsFastWhenInitialLoadFails() {
        SigningKeys signingKeys = mock(SigningKeys.class);
        given(signingKeys.load()).willThrow(new IllegalStateException("Key Vault unreachable"));

        assertThatThrownBy(() -> new RotatingJwkSource(signingKeys)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructorRejectsAnEmptyKeySet() {
        SigningKeys signingKeys = mock(SigningKeys.class);
        given(signingKeys.load()).willReturn(List.of());

        assertThatThrownBy(() -> new RotatingJwkSource(signingKeys)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no keys");
    }

    @Test
    void refreshReplacesTheKeySetOnSuccess() {
        RSAKey initial = generateKey();
        RSAKey rotated = generateKey();
        SigningKeys signingKeys = mock(SigningKeys.class);
        given(signingKeys.load()).willReturn(List.of(initial)).willReturn(List.of(rotated));
        RotatingJwkSource source = new RotatingJwkSource(signingKeys);

        source.refresh();

        assertThat(source.get(MATCH_ALL, null)).extracting(JWK::getKeyID).containsExactly(rotated.getKeyID());
    }

    @Test
    void refreshKeepsThePreviousKeySetWhenTheReloadFails() {
        RSAKey initial = generateKey();
        SigningKeys signingKeys = mock(SigningKeys.class);
        given(signingKeys.load()).willReturn(List.of(initial))
                .willThrow(new IllegalStateException("Key Vault unreachable"));
        RotatingJwkSource source = new RotatingJwkSource(signingKeys);

        source.refresh();

        assertThat(source.get(MATCH_ALL, null)).extracting(JWK::getKeyID).containsExactly(initial.getKeyID());
        verify(signingKeys, times(2)).load();
    }

    private static RSAKey generateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
