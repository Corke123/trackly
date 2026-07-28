package org.unibl.etf.pisio.identityservice.jwk;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.certificates.CertificateClient;
import com.azure.security.keyvault.certificates.CertificateClientBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.nimbusds.jose.jwk.JWK;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Wires a durable, rotating JWT signing key set in place of the ephemeral RSA key that
 * {@code OAuth2AuthorizationServerJwtAutoConfiguration} generates on every startup —
 * that default loses all outstanding sessions/tokens on a restart and serves a
 * different key per replica, which breaks multi-instance deployments and blue-green
 * rollouts.
 *
 * <p>The key source is selected by {@code trackly.jwt.signing.source}: {@code pem}
 * (default, for local development, see {@link PemSigningKeys}) or {@code keyvault}
 * (Azure, see {@link KeyVaultSigningKeys}). Either way the result is exposed as a single
 * {@link RotatingJwkSource} that {@link JwkRotationScheduler} reloads on a schedule, so
 * a rotation at the source — Key Vault's own AutoRenew, or a replaced PEM file — takes
 * effect without restarting the application.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtSigningProperties.class)
@EnableScheduling
public class JwkConfig {

    @Bean
    @ConditionalOnProperty(prefix = "trackly.jwt.signing", name = "source", havingValue = "pem", matchIfMissing = true)
    SigningKeys pemSigningKeys(JwtSigningProperties properties) {
        return new PemSigningKeys(properties.pem().locations());
    }

    @Bean
    @ConditionalOnProperty(prefix = "trackly.jwt.signing", name = "source", havingValue = "keyvault")
    SigningKeys keyVaultSigningKeys(JwtSigningProperties properties) {
        JwtSigningProperties.KeyVault keyVault = properties.keyVault();
        var credential = new DefaultAzureCredentialBuilder().build();
        CertificateClient certificateClient = new CertificateClientBuilder().vaultUrl(keyVault.uri())
                .credential(credential)
                .buildClient();
        SecretClient secretClient = new SecretClientBuilder().vaultUrl(keyVault.uri())
                .credential(credential)
                .buildClient();
        return new KeyVaultSigningKeys(certificateClient, secretClient, keyVault.certificateName(),
                properties.publishedVersions());
    }

    /**
     * The only bean exposed as {@code JWKSource<SecurityContext>} in this context -
     * Spring Security's own lookup for that type (used to register the JWK Set
     * endpoint filter and to build the default {@code JwtDecoder}) resolves a bean only
     * when there's exactly one match; since {@link RotatingJwkSource} directly
     * implements {@code JWKSource<SecurityContext>}, a second bean merely re-exposing it
     * under that interface type would make the match ambiguous and silently disable
     * both. Callers that need {@link RotatingJwkSource#refresh()} (see
     * {@link JwkRotationScheduler}) inject the concrete type instead.
     */
    @Bean
    RotatingJwkSource rotatingJwkSource(SigningKeys signingKeys) {
        return new RotatingJwkSource(signingKeys);
    }

    /**
     * {@link NimbusJwtEncoder}'s default {@code jwkSelector} throws whenever more than
     * one JWK matches the signing algorithm, which is the normal state during a
     * rotation window (retired public-only keys still match on key use + algorithm).
     * Select the one key that actually carries private material instead.
     */
    @Bean
    JwtEncoder jwtEncoder(RotatingJwkSource jwkSource) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
        encoder.setJwkSelector(jwks -> jwks.stream()
                .filter(JWK::isPrivate)
                .findFirst()
                .orElseThrow(() -> new JwtEncodingException("No signing key with private material available")));
        return encoder;
    }
}
