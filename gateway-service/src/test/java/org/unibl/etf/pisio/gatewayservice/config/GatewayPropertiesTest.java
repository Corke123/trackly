package org.unibl.etf.pisio.gatewayservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GatewayPropertiesTest {

    private static final URI FRONTEND = URI.create("http://localhost:4200");

    @Test
    @DisplayName("Given a one-segment api prefix, when the prefix segments are counted, then one segment is stripped")
    void countsSegmentsOfSingleSegmentPrefix() {
        GatewayProperties properties = new GatewayProperties("/api", false, FRONTEND, Map.of());

        assertThat(properties.apiPrefixSegments()).isEqualTo(1);
    }

    @Test
    @DisplayName("Given a nested api prefix, when the prefix segments are counted, then every segment is stripped")
    void countsSegmentsOfNestedPrefix() {
        GatewayProperties properties = new GatewayProperties("/trackly/api", false, FRONTEND, Map.of());

        assertThat(properties.apiPrefixSegments()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "/api/", "/"})
    @DisplayName(
            """
            Given an api prefix that is not an absolute path without a trailing slash, \
            when the properties are bound, \
            then they are rejected\
            """)
    void rejectsMalformedApiPrefix(String apiPrefix) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GatewayProperties(apiPrefix, false, FRONTEND, Map.of()))
                .withMessageContaining("trackly.gateway.api-prefix");
    }

    @Test
    @DisplayName(
            """
            Given no backends are configured, \
            when the properties are bound, \
            then the route table is empty rather than null\
            """)
    void defaultsMissingBackendsToNone() {
        GatewayProperties properties = new GatewayProperties("/api", false, FRONTEND, null);

        assertThat(properties.backends()).isEmpty();
    }

    @Test
    @DisplayName(
            """
            Given several backends are configured, \
            when the properties are bound, \
            then they keep the order they were declared in\
            """)
    void keepsBackendDeclarationOrder() {
        Map<String, GatewayProperties.Backend> declared = new LinkedHashMap<>();
        declared.put("board", new GatewayProperties.Backend(URI.create("http://board:8081"), List.of("/boards/**")));
        declared.put("notification", new GatewayProperties.Backend(URI.create("http://notification:8082"),
                List.of("/activity/**")));

        GatewayProperties properties = new GatewayProperties("/api", false, FRONTEND, declared);

        assertThat(properties.backends().keySet()).containsExactly("board", "notification");
        assertThat(properties.backends().get("board").paths()).containsExactly("/boards/**");
    }

    @Test
    @DisplayName(
            """
            Given a backend with no paths, \
            when the properties are bound, \
            then it contributes no routes rather than failing\
            """)
    void defaultsMissingPathsToNone() {
        GatewayProperties.Backend backend = new GatewayProperties.Backend(URI.create("http://board:8081"), null);

        assertThat(backend.paths()).isEmpty();
    }
}
