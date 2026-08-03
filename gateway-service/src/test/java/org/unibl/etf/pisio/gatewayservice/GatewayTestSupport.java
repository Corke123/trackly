package org.unibl.etf.pisio.gatewayservice;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Points every test's gateway at a stubbed authorization server, so the production
 * {@code application.yaml} — issuer, client registration and all — is what gets exercised.
 */
public abstract class GatewayTestSupport {

    private static final StubIdentityProvider IDENTITY = new StubIdentityProvider();

    @DynamicPropertySource
    static void identityService(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.provider.identity.issuer-uri", IDENTITY::issuer);
    }
}
