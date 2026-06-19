package com.awbd.cinema.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder, RedisRateLimiter rateLimiter, @Qualifier("ipKeyResolver") KeyResolver keyResolver) {
        return builder.routes()
                .route("user-service", r -> r
                        .path("/api/v1/auth/**", "/api/v1/user/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                        .uri("lb://user-service"))
                .route("catalog-service", r -> r
                        .path("/api/v1/movies/**", "/api/v1/rooms/**", "/api/v1/seats/**", "/api/v1/screen-sessions/**", "/api/v1/session-infos/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                        .uri("lb://catalog-service"))
                .route("booking-service", r -> r
                        .path("/api/v1/orders/**", "/api/v1/tickets/**", "/api/v1/ticket-info/**", "/api/v1/offers/**", "/api/v1/notifications/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                        .uri("lb://booking-service"))
                .build();
    }
}
