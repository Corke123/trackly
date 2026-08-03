package org.unibl.etf.pisio.gatewayservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayConfig {

    private static final String HTML_NAVIGATION = ".*text/html.*";

    private static final String INDEX_HTML = "/index.html";

    @Bean
    public RouteLocator gateway(RouteLocatorBuilder routeLocatorBuilder, GatewayProperties properties) {
        RouteLocatorBuilder.Builder routes = routeLocatorBuilder.routes();

        properties.backends().forEach((name, backend) -> {
            for (String path : backend.paths()) {
                routes.route(name + path, predicateSpec -> predicateSpec
                        .path(properties.apiPrefix() + path)
                        .filters(filterSpec -> filterSpec
                                .tokenRelay()
                                .stripPrefix(properties.apiPrefixSegments()))
                        .uri(backend.uri()));
            }
        });

        return (properties.serveSpa() ? bundledSpa(routes) : devServer(routes, properties)).build();
    }

    private RouteLocatorBuilder.Builder bundledSpa(RouteLocatorBuilder.Builder routes) {
        return routes.route("spa", predicateSpec -> predicateSpec
                .path("/**")
                .and()
                .header(HttpHeaders.ACCEPT, HTML_NAVIGATION)
                .and()
                .not(notSpec -> notSpec.path(INDEX_HTML))
                .filters(filterSpec -> filterSpec.setPath(INDEX_HTML))
                .uri("forward:/"));
    }

    private RouteLocatorBuilder.Builder devServer(RouteLocatorBuilder.Builder routes, GatewayProperties properties) {
        return routes.route("frontend-dev-server", predicateSpec -> predicateSpec
                .path("/**")
                .uri(properties.frontendUri()));
    }
}
