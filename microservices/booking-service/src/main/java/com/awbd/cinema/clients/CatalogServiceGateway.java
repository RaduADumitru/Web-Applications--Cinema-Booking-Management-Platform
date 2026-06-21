package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.TicketDTOs.BulkSaveTicketsDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.config.FeignClientErrorTranslator;
import com.awbd.cinema.exceptions.BadRequestException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resilience boundary for catalog-service calls: Resilience4j retry (transient faults only) +
 * circuit breaker, with a fallback that surfaces real 4xx business errors and degrades to
 * "unavailable" on a genuine outage. Callers inject this instead of {@link CatalogServiceClient}.
 *
 * <p>{@code @Retry} is the outermost aspect, so {@code fallbackMethod} lives on it and fires once,
 * after all retry attempts are exhausted (or immediately for a non-retryable cause such as a 4xx
 * or an open circuit).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogServiceGateway {

    private static final String NAME = "catalog-service";

    private final CatalogServiceClient catalogServiceClient;
    private final FeignClientErrorTranslator errorTranslator;

    @CircuitBreaker(name = NAME)
    @Retry(name = NAME, fallbackMethod = "getTicketSetupFallback")
    public TicketSetupDTO getTicketSetup(Long seatId, Long roomId, Long sessionId) {
        return catalogServiceClient.getTicketSetup(seatId, roomId, sessionId);
    }

    @CircuitBreaker(name = NAME)
    @Retry(name = NAME, fallbackMethod = "getTicketSetupsFallback")
    public List<TicketSetupDTO> getTicketSetups(BulkSaveTicketsDTO dto) {
        return catalogServiceClient.getTicketSetups(dto);
    }

    TicketSetupDTO getTicketSetupFallback(Long seatId, Long roomId, Long sessionId, Throwable cause) {
        // A real 4xx from catalog (session not found, seat not in room, …) surfaces with its
        // true status/message; only a genuine outage falls back to "unavailable".
        RuntimeException clientError = errorTranslator.clientErrorOrNull(cause);
        if (clientError != null) {
            throw clientError;
        }
        log.warn("catalog-service unavailable for ticket-setup (seat={}, room={}, session={}). Cause: {}",
                seatId, roomId, sessionId, cause.toString());
        throw new BadRequestException("Catalog service is currently unavailable. Please try again later.");
    }

    List<TicketSetupDTO> getTicketSetupsFallback(BulkSaveTicketsDTO dto, Throwable cause) {
        RuntimeException clientError = errorTranslator.clientErrorOrNull(cause);
        if (clientError != null) {
            throw clientError;
        }
        log.warn("catalog-service unavailable for bulk ticket-setup. Cause: {}", cause.toString());
        throw new BadRequestException("Catalog service is currently unavailable. Please try again later.");
    }
}
