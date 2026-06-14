package com.awbd.cinema.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Value("${services.user.uri}")
    private String userUri;

    @Value("${services.catalog.uri}")
    private String catalogUri;

    @Value("${services.booking.uri}")
    private String bookingUri;

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder, RedisRateLimiter rateLimiter, KeyResolver keyResolver) {
        return builder.routes()
                .route("user-service", r -> r
                        .path("/api/v1/auth/**", "/api/v1/user/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                        .uri(userUri))
                .route("catalog-service", r -> r
                        .path("/api/v1/movies/**", "/api/v1/rooms/**", "/api/v1/seats/**", "/api/v1/screen-sessions/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                        .uri(catalogUri))
                .route("booking-service", r -> r
                        .path("/api/v1/orders/**", "/api/v1/tickets/**", "/api/v1/ticket-info/**", "/api/v1/offers/**", "/api/v1/notifications/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                        .uri(bookingUri))
                .build();
    }
}
