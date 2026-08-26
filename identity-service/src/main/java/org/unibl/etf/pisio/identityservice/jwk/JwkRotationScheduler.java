package org.unibl.etf.pisio.identityservice.jwk;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically reloads the JWT signing key set so rotation at the source — Key Vault's
 * own AutoRenew, or a replaced PEM file locally — takes effect without a restart. See
 * {@link RotatingJwkSource#refresh()} for what happens on a failed reload.
 */
@Component
public class JwkRotationScheduler {

    private final RotatingJwkSource rotatingJwkSource;

    public JwkRotationScheduler(RotatingJwkSource rotatingJwkSource) {
        this.rotatingJwkSource = rotatingJwkSource;
    }

    @Scheduled(fixedDelayString = "${trackly.jwt.signing.refresh-interval:PT10M}",
            initialDelayString = "${trackly.jwt.signing.refresh-interval:PT10M}")
    void refresh() {
        this.rotatingJwkSource.refresh();
    }
}
