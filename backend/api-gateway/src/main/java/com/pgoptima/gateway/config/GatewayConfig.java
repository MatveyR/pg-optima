package com.pgoptima.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r
                        .path("/api/v1/auth/**")
                        .uri("http://localhost:8083"))
                .route("user-service", r -> r
                        .path("/api/v1/connections/**", "/api/v1/projects/**")
                        .uri("http://localhost:8082"))
                .route("analytics-service", r -> r
                        .path("/api/v1/optimization/**")
                        .uri("http://localhost:8081"))
                .route("swagger-aggregator", r -> r
                        .path("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                        .uri("http://localhost:8083"))
                .build();
    }
}