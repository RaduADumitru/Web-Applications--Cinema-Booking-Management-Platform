package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.config.FeignClientErrorTranslator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resilience boundary for booking-service notification calls: Resilience4j retry (transient faults
 * only) + circuit breaker. Notifications are best-effort, so the fallback surfaces a real 4xx (a
 * genuine bug in the request) but skips silently on a genuine outage rather than failing the caller
 * (e.g. registration must still succeed if notifications are down). Callers inject this instead of
 * {@link BookingServiceClient}.
 *
 * <p>{@code @Retry} is the outermost aspect, so {@code fallbackMethod} lives on it and fires once,
 * after all retry attempts are exhausted (or immediately for a non-retryable cause such as a 4xx
 * or an open circuit).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationGateway {

    private static final String NAME = "booking-service";

    private final BookingServiceClient bookingServiceClient;
    private final FeignClientErrorTranslator errorTranslator;

    @CircuitBreaker(name = NAME)
    @Retry(name = NAME, fallbackMethod = "createNotificationFallback")
    public void createNotification(CreateNotificationDTO dto) {
        bookingServiceClient.createNotification(dto);
    }

    void createNotificationFallback(CreateNotificationDTO dto, Throwable cause) {
        // A real 4xx (e.g. a malformed notification request) is surfaced so the bug is not
        // swallowed; only a genuine booking-service outage is skipped silently.
        RuntimeException clientError = errorTranslator.clientErrorOrNull(cause);
        if (clientError != null) {
            throw clientError;
        }
        log.warn("booking-service unavailable; skipping notification of type {} for user {}. Cause: {}",
                dto.type(), dto.userId(), cause.toString());
    }
}
