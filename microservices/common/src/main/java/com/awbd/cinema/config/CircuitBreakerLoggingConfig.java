package com.awbd.cinema.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;

/**
 * Logs Resilience4j retry and circuit-breaker activity (retry attempts, recorded failures, state
 * transitions and rejected calls) for every gateway across all services that depend on
 * {@code common}. This surfaces retry / fallback / circuit-breaker behaviour that would otherwise
 * be silent and is the basis for demonstrating error handling by stopping a downstream service.
 * Messages are logged at WARN, which is intentionally console-only (the file appender threshold is
 * ERROR).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CircuitBreakerLoggingConfig {

    private final ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider;
    private final ObjectProvider<RetryRegistry> retryRegistryProvider;

    @PostConstruct
    void registerEventLogging() {
        registerCircuitBreakerLogging();
        registerRetryLogging();
    }

    private void registerCircuitBreakerLogging() {
        CircuitBreakerRegistry registry = circuitBreakerRegistryProvider.getIfAvailable();
        if (registry == null) {
            log.debug("No CircuitBreakerRegistry present; circuit-breaker event logging disabled.");
            return;
        }
        // Circuit breakers are created lazily on first use, so bind both existing and future ones.
        registry.getAllCircuitBreakers().forEach(this::bindLogging);
        registry.getEventPublisher().onEntryAdded(event -> bindLogging(event.getAddedEntry()));
    }

    private void registerRetryLogging() {
        RetryRegistry registry = retryRegistryProvider.getIfAvailable();
        if (registry == null) {
            log.debug("No RetryRegistry present; retry event logging disabled.");
            return;
        }
        registry.getAllRetries().forEach(this::bindLogging);
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

    private void bindLogging(Retry retry) {
        String name = retry.getName();
        retry.getEventPublisher()
                .onRetry(event -> log.warn("Retry '{}' attempt #{} failed ({}), retrying in {}ms",
                        name,
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable() == null ? "unknown" : event.getLastThrowable().toString(),
                        event.getWaitInterval().toMillis()))
                .onError(event -> log.warn("Retry '{}' exhausted after {} attempts: {}",
                        name,
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable() == null ? "unknown" : event.getLastThrowable().toString()));
    }
}
