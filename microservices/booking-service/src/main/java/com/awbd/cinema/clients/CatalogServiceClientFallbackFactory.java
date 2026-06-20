package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.TicketDTOs.BulkSaveTicketsDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.InvalidFieldException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.exceptions.TooManyRequestsException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogServiceClientFallbackFactory implements FallbackFactory<CatalogServiceClient> {

    private final ObjectMapper objectMapper;

    @Override
    public CatalogServiceClient create(Throwable cause) {
        return new CatalogServiceClient() {
            @Override
            public TicketSetupDTO getTicketSetup(Long seatId, Long roomId, Long sessionId) {
                FeignException clientError = clientError(cause);
                if (clientError != null) {
                    // A real client (4xx) error from catalog — e.g. "Screen session not found." or
                    // "Seat does not belong to the specified room." Surface it with its true status
                    // and message instead of masking it as an outage.
                    throw translate(clientError);
                }
                // Genuine availability failure (5xx, timeout, connection error, circuit open).
                log.warn("catalog-service unavailable for ticket-setup (seat={}, room={}, session={}). Cause: {}",
                        seatId, roomId, sessionId, cause.toString());
                throw new BadRequestException("Catalog service is currently unavailable. Please try again later.");
            }

            @Override
            public List<TicketSetupDTO> getTicketSetups(BulkSaveTicketsDTO dto) {
                FeignException clientError = clientError(cause);
                if (clientError != null) {
                    throw translate(clientError);
                }
                log.warn("catalog-service unavailable for bulk ticket-setup. Cause: {}", cause.toString());
                throw new BadRequestException("Catalog service is currently unavailable. Please try again later.");
            }
        };
    }

    /** Returns the {@link FeignException} in the cause chain iff it represents a 4xx client error. */
    private static FeignException clientError(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof FeignException fe && fe.status() >= 400 && fe.status() < 500) {
                return fe;
            }
        }
        return null;
    }

    /** Re-throw catalog's 4xx as the matching exception so booking's handler maps it to the same status. */
    private RuntimeException translate(FeignException fe) {
        String message = extractMessage(fe, "The request could not be completed.");
        return switch (fe.status()) {
            case 404 -> new NotFoundException(message);
            case 409 -> new AlreadyExistsException(message);
            case 422 -> new InvalidFieldException(message);
            case 429 -> new TooManyRequestsException(message);
            default -> new BadRequestException(message);
        };
    }

    /** Pulls the {@code message} field out of catalog's JSON error body, defaulting if unparseable. */
    private String extractMessage(FeignException fe, String fallback) {
        try {
            String body = fe.contentUTF8();
            if (body != null && !body.isBlank()) {
                JsonNode message = objectMapper.readTree(body).get("message");
                if (message != null && !message.isNull() && !message.asText().isBlank()) {
                    return message.asText();
                }
            }
        } catch (Exception ignored) {
            // fall through to the default message
        }
        return fallback;
    }
}
