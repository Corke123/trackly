package org.unibl.etf.pisio.identityservice.jwk;

import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PemSigningKeysTest {

    private static final Resource NEWEST = new ClassPathResource("jwt/dev-signing-key.pem");
    private static final Resource PREVIOUS = new ClassPathResource("jwt/dev-signing-key-previous.pem");

    @Test
    void loadsKeysNewestFirstWithPrivateMaterialAndDistinctKeyIds() {
        List<RSAKey> keys = new PemSigningKeys(List.of(NEWEST, PREVIOUS)).load();

        assertThat(keys).hasSize(2);
        assertThat(keys.get(0).getKeyID()).isNotEqualTo(keys.get(1).getKeyID());
        assertThat(keys).allSatisfy(key -> {
            assertThat(key.isPrivate()).isTrue();
            assertThat(key.getKeyUse()).isEqualTo(KeyUse.SIGNATURE);
            assertThat(key.getAlgorithm().getName()).isEqualTo("RS256");
        });
    }

    @Test
    void keyIdIsStableAcrossReloads() {
        SigningKeys signingKeys = new PemSigningKeys(List.of(NEWEST, PREVIOUS));

        String firstLoadKid = signingKeys.load().getFirst().getKeyID();
        String secondLoadKid = signingKeys.load().getFirst().getKeyID();

        assertThat(firstLoadKid).isEqualTo(secondLoadKid);
    }

    @Test
    void rejectsEmptyLocationList() {
        assertThatThrownBy(() -> new PemSigningKeys(List.of())).isInstanceOf(IllegalStateException.class);
    }
}
