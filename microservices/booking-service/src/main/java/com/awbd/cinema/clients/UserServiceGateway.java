package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.config.FeignClientErrorTranslator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resilience boundary for user-service calls (loyalty points): Resilience4j retry (transient faults
 * only) + circuit breaker. A real 4xx is surfaced via the translator; on a genuine outage the
 * fallback degrades gracefully so the booking flow can still proceed (treat balance as 0 / skip the
 * update). Callers inject this instead of {@link UserServiceClient}.
 *
 * <p>{@code @Retry} is the outermost aspect, so {@code fallbackMethod} lives on it and fires once
 * after all retry attempts are exhausted, or immediately for a non-retryable cause such as a 4xx
 * or an open circuit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceGateway {

    private static final String NAME = "user-service";

    private final UserServiceClient userServiceClient;
    private final FeignClientErrorTranslator errorTranslator;

    @CircuitBreaker(name = NAME)
    @Retry(name = NAME, fallbackMethod = "getLoyaltyPointsFallback")
    public LoyaltyPointsDTO getLoyaltyPoints(Long id) {
        return userServiceClient.getLoyaltyPoints(id);
    }

    @CircuitBreaker(name = NAME)
    @Retry(name = NAME, fallbackMethod = "updateLoyaltyPointsFallback")
    public LoyaltyPointsDTO updateLoyaltyPoints(Long id, AdjustLoyaltyPointsDTO dto) {
        return userServiceClient.updateLoyaltyPoints(id, dto);
    }

    LoyaltyPointsDTO getLoyaltyPointsFallback(Long id, Throwable cause) {
        // A real 4xx (user not found / bad request) is surfaced; only a genuine outage degrades
        // to 0 points so the booking flow can still proceed.
        RuntimeException clientError = errorTranslator.clientErrorOrNull(cause);
        if (clientError != null) {
            throw clientError;
        }
        log.warn("user-service unavailable; treating loyalty points as 0 for user {}. Cause: {}",
                id, cause.toString());
        return new LoyaltyPointsDTO(id, 0);
    }

    LoyaltyPointsDTO updateLoyaltyPointsFallback(Long id, AdjustLoyaltyPointsDTO dto, Throwable cause) {
        // A real 4xx is surfaced; only a genuine outage skips the update and echoes the requested value.
        RuntimeException clientError = errorTranslator.clientErrorOrNull(cause);
        if (clientError != null) {
            throw clientError;
        }
        log.warn("user-service unavailable; skipping loyalty-points update for user {}. Cause: {}",
                id, cause.toString());
        return new LoyaltyPointsDTO(id, dto.loyaltyPoints());
    }

    /**
     * Like {@link #updateLoyaltyPoints} but never silently degrades — throws on any outage.
     * Used by saga steps where a silent skip would leave loyalty state inconsistent.
     */
    @CircuitBreaker(name = NAME)
    @Retry(name = NAME, fallbackMethod = "updateLoyaltyPointsStrictFallback")
    public LoyaltyPointsDTO updateLoyaltyPointsStrict(Long id, AdjustLoyaltyPointsDTO dto) {
        return userServiceClient.updateLoyaltyPoints(id, dto);
    }

    LoyaltyPointsDTO updateLoyaltyPointsStrictFallback(Long id, AdjustLoyaltyPointsDTO dto, Throwable cause) {
        RuntimeException clientError = errorTranslator.clientErrorOrNull(cause);
        if (clientError != null) {
            throw clientError;
        }
        throw new RuntimeException(
                "user-service unavailable; cannot update loyalty points for user " + id + " — saga will compensate",
                cause);
    }
}
