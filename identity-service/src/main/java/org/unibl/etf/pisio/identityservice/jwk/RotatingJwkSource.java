package org.unibl.etf.pisio.identityservice.jwk;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link JWKSource} whose key set is loaded from a {@link SigningKeys} at startup and
 * then reloaded on a schedule, so certificate/key rotation at the source (Key Vault
 * AutoRenew, or a replaced PEM file) is picked up without a restart.
 *
 * <p>The initial load happens in the constructor and fails fast — the application must
 * not start serving traffic with no signing key. A failed scheduled refresh is logged
 * and otherwise ignored: the last known-good key set stays in effect, since a transient
 * Key Vault outage should degrade rotation freshness, not availability.
 */
public class RotatingJwkSource implements JWKSource<SecurityContext> {

    private static final Logger log = LoggerFactory.getLogger(RotatingJwkSource.class);

    private final SigningKeys signingKeys;

    private final AtomicReference<JWKSet> current;

    public RotatingJwkSource(SigningKeys signingKeys) {
        this.signingKeys = signingKeys;
        this.current = new AtomicReference<>(toJwkSet(signingKeys.load()));
    }

    /**
     * Reloads the key set from the source. Invoked on {@code trackly.jwt.signing.refresh-interval}
     * by {@link JwkConfig}.
     */
    public void refresh() {
        try {
            this.current.set(toJwkSet(this.signingKeys.load()));
            log.debug("Refreshed JWT signing key set");
        } catch (RuntimeException ex) {
            log.warn("Failed to refresh JWT signing key set; keeping the previous one in effect", ex);
        }
    }

    @Override
    public List<JWK> get(JWKSelector jwkSelector, SecurityContext context) {
        return jwkSelector.select(this.current.get());
    }

    private static JWKSet toJwkSet(List<RSAKey> keys) {
        if (keys.isEmpty()) {
            throw new IllegalStateException("SigningKeys returned no keys");
        }
        return new JWKSet(new ArrayList<>(keys));
    }
}
