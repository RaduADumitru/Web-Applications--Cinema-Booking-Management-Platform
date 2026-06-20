package com.awbd.cinema.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;

/**
 * Logs resilience4j circuit-breaker activity (recorded failures, state transitions and rejected calls)
 * for every Feign client across all services that depend on {@code common}. This surfaces fallback /
 * circuit-breaker invocation that would otherwise be silent. Messages are logged at WARN, which is
 * intentionally console-only (the file appender threshold is ERROR).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CircuitBreakerLoggingConfig {

    private final ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider;

    @PostConstruct
    void registerEventLogging() {
        CircuitBreakerRegistry registry = circuitBreakerRegistryProvider.getIfAvailable();
        if (registry == null) {
            log.debug("No CircuitBreakerRegistry present; circuit-breaker event logging disabled.");
            return;
        }
        // Circuit breakers are created lazily on first use, so bind both existing and future ones.
        registry.getAllCircuitBreakers().forEach(this::bindLogging);
        registry.getEventPublisher().onEntryAdded(event -> bindLogging(event.getAddedEntry()));
    }

    private void bindLogging(CircuitBreaker circuitBreaker) {
        String name = circuitBreaker.getName();
        circuitBreaker.getEventPublisher()
                .onError(event -> log.warn("CircuitBreaker '{}' recorded a failed call: {}",
                        name, event.getThrowable().toString()))
                .onStateTransition(event -> log.warn("CircuitBreaker '{}' state transition {}",
                        name, event.getStateTransition()))
                .onCallNotPermitted(event -> log.warn("CircuitBreaker '{}' rejected a call (circuit is OPEN).",
                        name));
    }
}
