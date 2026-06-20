package com.awbd.cinema.config;

import feign.FeignException;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CircuitBreakerConfigurationTest {

    private final Predicate<Throwable> ignore =
            CircuitBreakerConfiguration.benignClientErrorsIgnored().getIgnoreExceptionPredicate();

    @Test
    void ignoresBenignClientErrors_soTheyDoNotTripTheBreaker() {
        assertThat(ignore.test(mock(FeignException.NotFound.class))).isTrue();
        assertThat(ignore.test(mock(FeignException.BadRequest.class))).isTrue();
        assertThat(ignore.test(mock(FeignException.Conflict.class))).isTrue();
        assertThat(ignore.test(mock(FeignException.UnprocessableEntity.class))).isTrue();
    }

    @Test
    void stillCountsOverloadServerAndInfrastructureFailures() {
        // 429 is an overload signal — the breaker must still react to it.
        assertThat(ignore.test(mock(FeignException.TooManyRequests.class))).isFalse();
        // generic Feign error (covers 5xx) and non-Feign failures (timeouts, connection refused).
        assertThat(ignore.test(mock(FeignException.class))).isFalse();
        assertThat(ignore.test(new RuntimeException("connection refused"))).isFalse();
    }
}
