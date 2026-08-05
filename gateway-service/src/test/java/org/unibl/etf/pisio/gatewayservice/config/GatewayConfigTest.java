package org.unibl.etf.pisio.gatewayservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.unibl.etf.pisio.gatewayservice.GatewayTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayConfigTest extends GatewayTestSupport {

    @Nested
    @SpringBootTest(properties = "trackly.gateway.serve-spa=false")
    @DisplayName("With the SPA proxied to the dev server")
    class DevServerMode {

        @Autowired
        private RouteLocator routeLocator;

        @Test
        @DisplayName("Given the default configuration, when the route table is built, then each backend path is routed under the api prefix and the SPA is last")
        void routesEachBackendPathThenTheSpa() {
            List<Route> routes = routeLocator.getRoutes().collectList().block();

            assertThat(routes).extracting(Route::getId)
                    .containsExactly("board/boards/**", "board/tickets/**", "notification/activity/**",
                            "identity/users/**", "frontend-dev-server");
            assertThat(routes).extracting(route -> route.getUri().toString())
                    .containsExactly("http://localhost:8081", "http://localhost:8081", "http://localhost:8082",
                            "http://localhost:9000", "http://localhost:4200");
        }

        @Test
        @DisplayName("Given a backend route, when its filters are inspected, then the access token is relayed and the api prefix is stripped")
        void relaysTheTokenAndStripsThePrefix() {
            Route boardRoute = routeLocator.getRoutes()
                    .filter(route -> "board/boards/**".equals(route.getId()))
                    .blockFirst();

            assertThat(boardRoute).isNotNull();
            assertThat(boardRoute.getFilters()).hasSize(2);
            assertThat(boardRoute.getFilters().toString()).contains("TokenRelay", "StripPrefix");
        }
    }

    @Nested
    @SpringBootTest(properties = "trackly.gateway.serve-spa=true")
    @DisplayName("With the SPA served from this image")
    class BundledSpaMode {

        @Autowired
        private RouteLocator routeLocator;

        @Test
        @DisplayName("Given the SPA is bundled, when the route table is built, then the dev server is not routed to at all")
        void replacesTheDevServerRouteWithTheBundledSpa() {
            List<Route> routes = routeLocator.getRoutes().collectList().block();

            assertThat(routes).extracting(Route::getId).containsExactly(
                    "board/boards/**", "board/tickets/**", "notification/activity/**", "identity/users/**", "spa");
            assertThat(routes).extracting(route -> route.getUri().toString()).contains("forward:/");
        }
    }
}
