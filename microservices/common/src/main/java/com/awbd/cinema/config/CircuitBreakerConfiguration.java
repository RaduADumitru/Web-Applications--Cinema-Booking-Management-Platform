package com.awbd.cinema.config;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tunes every Feign circuit breaker (in any service that depends on {@code common}) so that benign
 * client (4xx) errors do not trip the breaker.
 *
 * <p>A {@code 404}/{@code 400}/{@code 409}/{@code 422} means the downstream is healthy and is
 * correctly rejecting a specific request, so counting them as breaker failures would let, say, a
 * burst of "not found" responses open the breaker and make a healthy service look down. In
 * contrast {@code 429} (overload) and {@code 5xx}/timeouts are deliberately left counting, so the
 * breaker still backs off when the downstream is genuinely struggling.
 */
@Configuration
public class CircuitBreakerConfiguration {

    static CircuitBreakerConfig benignClientErrorsIgnored() {
        return CircuitBreakerConfig.custom()
                .ignoreExceptions(
                        FeignException.NotFound.class,            // 404
                        FeignException.BadRequest.class,          // 400
                        FeignException.Conflict.class,            // 409
                        FeignException.UnprocessableEntity.class) // 422
                .build();
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> ignoreBenignClientErrors() {
        // Keep the framework-default time limiter; only add the ignore rules.
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(benignClientErrorsIgnored())
                .timeLimiterConfig(TimeLimiterConfig.ofDefaults())
                .build());
    }
}
