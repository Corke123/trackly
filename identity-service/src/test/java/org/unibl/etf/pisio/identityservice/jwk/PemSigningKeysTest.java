package org.unibl.etf.pisio.identityservice.jwk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

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
        List<Resource> noLocations = List.of();

        assertThatThrownBy(() -> new PemSigningKeys(noLocations)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKeyThatIsNotRsaWithCrtParameters() throws Exception {
        SigningKeys signingKeys = new PemSigningKeys(List.of(ellipticCurveKey()));

        assertThatThrownBy(signingKeys::load).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsWhenKeyLocationCannotBeRead() {
        SigningKeys signingKeys = new PemSigningKeys(List.of(new ClassPathResource("jwt/no-such-signing-key.pem")));

        assertThatThrownBy(signingKeys::load).isInstanceOf(UncheckedIOException.class);
    }

    private static Resource ellipticCurveKey() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        byte[] pkcs8 = generator.generateKeyPair().getPrivate().getEncoded();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(pkcs8)
                + "\n-----END PRIVATE KEY-----\n";
        return new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII));
    }
}
