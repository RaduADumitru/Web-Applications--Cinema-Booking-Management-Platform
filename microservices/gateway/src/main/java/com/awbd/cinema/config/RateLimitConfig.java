package com.awbd.cinema.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    /** Rate-limit per calling client IP. */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "unknown");
    }

    @Bean
    public RedisRateLimiter redisRateLimiter(
            @Value("${gateway.rate-limit.replenish-rate:50}") int replenishRate,
            @Value("${gateway.rate-limit.burst-capacity:100}") int burstCapacity) {
        return new RedisRateLimiter(replenishRate, burstCapacity);
    }
}
