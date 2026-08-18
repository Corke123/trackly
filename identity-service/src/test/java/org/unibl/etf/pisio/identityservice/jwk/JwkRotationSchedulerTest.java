package org.unibl.etf.pisio.identityservice.jwk;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwkRotationSchedulerTest {

    @Test
    void refreshReloadsTheSigningKeySet() {
        RotatingJwkSource rotatingJwkSource = mock(RotatingJwkSource.class);

        new JwkRotationScheduler(rotatingJwkSource).refresh();

        verify(rotatingJwkSource).refresh();
    }
}
