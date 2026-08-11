package org.unibl.etf.pisio.identityservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trackly.client")
public record RegisteredClientProperties(
        String id, String secret, String redirectUri, String postLogoutRedirectUri) {}
