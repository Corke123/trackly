package org.unibl.etf.pisio.gatewayservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties("trackly.gateway")
public record GatewayProperties(String apiPrefix, boolean serveSpa, URI frontendUri, Map<String, Backend> backends) {

    public GatewayProperties {
        if (!apiPrefix.startsWith("/") || apiPrefix.endsWith("/")) {
            throw new IllegalArgumentException(
                    "trackly.gateway.api-prefix must start with '/' and must not end with '/', but was '" + apiPrefix + "'");
        }

        backends = (backends == null) ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(backends));
    }

    int apiPrefixSegments() {
        return apiPrefix.split("/").length - 1;
    }

    public record Backend(URI uri, List<String> paths) {

        public Backend {
            paths = (paths == null) ? List.of() : List.copyOf(paths);
        }
    }
}
