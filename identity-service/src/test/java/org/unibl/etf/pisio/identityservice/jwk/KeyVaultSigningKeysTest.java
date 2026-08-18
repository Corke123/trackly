package org.unibl.etf.pisio.identityservice.jwk;

import com.azure.core.http.rest.PagedIterable;
import com.azure.security.keyvault.certificates.CertificateClient;
import com.azure.security.keyvault.certificates.models.CertificateProperties;
import com.azure.security.keyvault.certificates.models.KeyVaultCertificate;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KeyVaultSigningKeysTest {

    private static final String CERTIFICATE_NAME = "trackly-signing";

    private static final String SIGNING_PKCS12 = text("keyvault/self-signed-test-keystore-pkcs12.base64");

    private static final byte[] PREVIOUS_CERTIFICATE = bytes("keyvault/previous-certificate.der");

    private CertificateClient certificateClient;

    private SecretClient secretClient;

    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        this.certificateClient = mock(CertificateClient.class);
        this.secretClient = mock(SecretClient.class);
        this.now = OffsetDateTime.now();
    }

    @Test
    void loadsNewestVersionWithPrivateKeyAndOlderVersionsPublicOnly() {
        versionsAre(version("old", this.now.minusDays(2), true), version("new", this.now, true));
        signingMaterialIs(SIGNING_PKCS12, "new");
        publicCertificateIs(PREVIOUS_CERTIFICATE, "old");

        List<RSAKey> keys = signingKeys(5).load();

        assertThat(keys).extracting(RSAKey::getKeyID).containsExactly("new", "old");
        assertThat(keys.getFirst().isPrivate()).isTrue();
        assertThat(keys.getLast().isPrivate()).isFalse();
        assertThat(keys).allSatisfy(key -> {
            assertThat(key.getKeyUse()).isEqualTo(KeyUse.SIGNATURE);
            assertThat(key.getAlgorithm().getName()).isEqualTo("RS256");
        });
    }

    @Test
    void ignoresDisabledVersions() {
        versionsAre(version("disabled", this.now, false), version("enabled", this.now.minusDays(1), true));
        signingMaterialIs(SIGNING_PKCS12, "enabled");

        List<RSAKey> keys = signingKeys(5).load();

        assertThat(keys).extracting(RSAKey::getKeyID).containsExactly("enabled");
    }

    @Test
    void publishesAtMostTheConfiguredNumberOfVersions() {
        versionsAre(version("newest", this.now, true), version("middle", this.now.minusDays(1), true),
                version("oldest", this.now.minusDays(2), true));
        signingMaterialIs(SIGNING_PKCS12, "newest");
        publicCertificateIs(PREVIOUS_CERTIFICATE, "middle");

        List<RSAKey> keys = signingKeys(2).load();

        assertThat(keys).extracting(RSAKey::getKeyID).containsExactly("newest", "middle");
    }

    @Test
    void failsWhenNoVersionIsEnabled() {
        versionsAre(version("disabled", this.now, false));

        assertThatThrownBy(() -> signingKeys(5).load()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CERTIFICATE_NAME);
    }

    @Test
    void failsWhenSigningMaterialIsNotAPkcs12Store() {
        versionsAre(version("new", this.now, true));
        signingMaterialIs(Base64.getEncoder().encodeToString("not a pkcs12 store".getBytes(StandardCharsets.UTF_8)),
                "new");

        assertThatThrownBy(() -> signingKeys(5).load()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("new");
    }

    @Test
    void failsWhenAnOlderCertificateCannotBeParsed() {
        versionsAre(version("new", this.now, true), version("old", this.now.minusDays(1), true));
        signingMaterialIs(SIGNING_PKCS12, "new");
        publicCertificateIs("not a certificate".getBytes(StandardCharsets.UTF_8), "old");

        assertThatThrownBy(() -> signingKeys(5).load()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("old");
    }

    private KeyVaultSigningKeys signingKeys(int publishedVersions) {
        return new KeyVaultSigningKeys(this.certificateClient, this.secretClient, CERTIFICATE_NAME, publishedVersions);
    }

    private CertificateProperties version(String version, OffsetDateTime createdOn, boolean enabled) {
        CertificateProperties properties = mock(CertificateProperties.class);
        when(properties.getVersion()).thenReturn(version);
        when(properties.getCreatedOn()).thenReturn(createdOn);
        when(properties.isEnabled()).thenReturn(enabled);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private void versionsAre(CertificateProperties... versions) {
        PagedIterable<CertificateProperties> page = mock(PagedIterable.class);
        when(page.stream()).thenReturn(Stream.of(versions));
        when(this.certificateClient.listPropertiesOfCertificateVersions(CERTIFICATE_NAME)).thenReturn(page);
    }

    private void signingMaterialIs(String pkcs12Base64, String version) {
        KeyVaultSecret secret = mock(KeyVaultSecret.class);
        when(secret.getValue()).thenReturn(pkcs12Base64);
        when(this.secretClient.getSecret(CERTIFICATE_NAME, version)).thenReturn(secret);
    }

    private void publicCertificateIs(byte[] der, String version) {
        KeyVaultCertificate certificate = mock(KeyVaultCertificate.class);
        when(certificate.getCer()).thenReturn(der);
        when(this.certificateClient.getCertificateVersion(CERTIFICATE_NAME, version)).thenReturn(certificate);
    }

    private static String text(String name) {
        return new String(bytes(name), StandardCharsets.US_ASCII).trim();
    }

    private static byte[] bytes(String name) {
        try (InputStream stream = KeyVaultSigningKeysTest.class.getClassLoader().getResourceAsStream(name)) {
            return stream.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
