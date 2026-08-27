package org.unibl.etf.pisio.identityservice.jwk;

import static java.util.Comparator.comparing;

import com.azure.security.keyvault.certificates.CertificateClient;
import com.azure.security.keyvault.certificates.models.CertificateProperties;
import com.azure.security.keyvault.certificates.models.KeyVaultCertificate;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Loads the RSA signing key set from the enabled versions of an Azure Key Vault
 * certificate, newest first.
 *
 * <p>The certificate must be created with an exportable RSA key and a self-signed
 * AutoRenew lifetime action (see the service README), so Key Vault mints a new version
 * on its own schedule without any code here having to drive rotation. Each certificate
 * version publishes its secret under the same name/version, so:
 * <ul>
 * <li>the newest enabled version's private key is read via {@link SecretClient} as a
 * base64-encoded PKCS#12 store (the certificate's {@code secretProperties.contentType}
 * is set to {@code application/x-pkcs12} when the certificate is created), and</li>
 * <li>older enabled versions contribute only their public key, read via
 * {@link CertificateClient#getCertificateVersion}, since Key Vault does not allow
 * exporting the private key of a non-current certificate version.</li>
 * </ul>
 * The Key Vault certificate version string is used directly as the {@code kid}.
 */
public class KeyVaultSigningKeys implements SigningKeys {

    private static final char[] NO_PASSWORD = new char[0];

    private final CertificateClient certificateClient;

    private final SecretClient secretClient;

    private final String certificateName;

    private final int publishedVersions;

    public KeyVaultSigningKeys(CertificateClient certificateClient, SecretClient secretClient, String certificateName,
                               int publishedVersions) {
        this.certificateClient = certificateClient;
        this.secretClient = secretClient;
        this.certificateName = certificateName;
        this.publishedVersions = publishedVersions;
    }

    @Override
    public List<RSAKey> load() {
        List<CertificateProperties> certificateProperties = certificateClient
                .listPropertiesOfCertificateVersions(certificateName)
                .stream()
                .filter(version -> Boolean.TRUE.equals(version.isEnabled()))
                .sorted(comparing(CertificateProperties::getCreatedOn).reversed())
                .limit(publishedVersions)
                .toList();

        if (certificateProperties.isEmpty()) {
            throw new IllegalStateException(
                    "No enabled certificate versions found for '" + this.certificateName + "' in Key Vault");
        }

        RSAKey signingKey = readSigningKey(certificateProperties.getFirst());
        List<RSAKey> publicKeys = certificateProperties.stream()
                .skip(1)
                .map(this::readPublicKey)
                .toList();

        List<RSAKey> keys = new ArrayList<>(certificateProperties.size());
        keys.add(signingKey);
        keys.addAll(publicKeys);
        return keys;
    }

    private RSAKey readSigningKey(CertificateProperties version) {
        KeyVaultSecret secret = this.secretClient.getSecret(this.certificateName, version.getVersion());
        byte[] pkcs12 = Base64.getDecoder().decode(secret.getValue());
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(pkcs12), NO_PASSWORD);
            String alias = keyStore.aliases().nextElement();
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, NO_PASSWORD);
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
            return buildKey((RSAPublicKey) certificate.getPublicKey(), privateKey, version.getVersion());
        } catch (GeneralSecurityException | IOException ex) {
            throw new IllegalStateException(
                    "Failed to load signing key for certificate version " + version.getVersion(), ex);
        }
    }

    private RSAKey readPublicKey(CertificateProperties version) {
        KeyVaultCertificate certificate = this.certificateClient.getCertificateVersion(this.certificateName,
                version.getVersion());
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate x509Certificate = (X509Certificate) certificateFactory
                    .generateCertificate(new ByteArrayInputStream(certificate.getCer()));
            return buildKey((RSAPublicKey) x509Certificate.getPublicKey(), null, version.getVersion());
        } catch (CertificateException ex) {
            throw new IllegalStateException("Failed to parse certificate for version " + version.getVersion(), ex);
        }
    }

    private static RSAKey buildKey(RSAPublicKey publicKey, PrivateKey privateKey, String kid) {
        RSAKey.Builder builder = new RSAKey.Builder(publicKey).keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID(kid);
        if (privateKey != null) {
            builder.privateKey(privateKey);
        }
        return builder.build();
    }
}
