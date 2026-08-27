package org.unibl.etf.pisio.identityservice.jwk;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("trackly.jwt.signing")
public record JwtSigningProperties(String source, Duration refreshInterval, int publishedVersions, Pem pem,
                                   KeyVault keyVault) {

    public JwtSigningProperties {
        pem = (pem != null) ? pem : new Pem(null);
        keyVault = (keyVault != null) ? keyVault : new KeyVault(null, "trackly-jwt-signing");
    }

    public record Pem(List<Resource> locations) {

        public Pem {
            locations = (locations != null) ? locations : List.of();
        }
    }

    public record KeyVault(String uri, String certificateName) {
    }
}
