package org.unibl.etf.pisio.identityservice.jwk;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.ssl.pem.PemContent;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.List;

public class PemSigningKeys implements SigningKeys {

    private final List<Resource> locations;

    public PemSigningKeys(List<Resource> locations) {
        if (locations.isEmpty()) {
            throw new IllegalStateException(
                    "trackly.jwt.signing.pem.locations must list at least one PEM key when trackly.jwt.signing.source=pem");
        }
        this.locations = locations;
    }

    @Override
    public List<RSAKey> load() {
        List<RSAKey> keys = new ArrayList<>(this.locations.size());
        for (Resource location : this.locations) {
            keys.add(readKey(location));
        }
        return keys;
    }

    private RSAKey readKey(Resource location) {
        RSAPrivateCrtKey privateKey = readPrivateKey(location);
        try {
            RSAPublicKey publicKey = derivePublicKey(privateKey);
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
        } catch (GeneralSecurityException | JOSEException ex) {
            throw new IllegalStateException("Failed to derive signing key from " + location, ex);
        }
    }

    private static RSAPrivateCrtKey readPrivateKey(Resource location) {
        try (InputStream in = location.getInputStream()) {
            PrivateKey privateKey = PemContent.load(in).getPrivateKey();
            if (!(privateKey instanceof RSAPrivateCrtKey rsaPrivateKey)) {
                throw new IllegalStateException(
                        "Expected an RSA private key with CRT parameters (PKCS#8) in " + location);
            }
            return rsaPrivateKey;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read signing key from " + location, ex);
        }
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateCrtKey privateKey) throws GeneralSecurityException {
        RSAPublicKeySpec spec = new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
