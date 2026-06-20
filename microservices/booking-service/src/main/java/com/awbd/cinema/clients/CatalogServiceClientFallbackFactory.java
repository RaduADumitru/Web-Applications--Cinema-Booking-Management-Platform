package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.TicketDTOs.BulkSaveTicketsDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.config.FeignClientErrorTranslator;
import com.awbd.cinema.exceptions.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogServiceClientFallbackFactory implements FallbackFactory<CatalogServiceClient> {

    private final FeignClientErrorTranslator errorTranslator;

    @Override
    public CatalogServiceClient create(Throwable cause) {
        return new CatalogServiceClient() {
            @Override
            public TicketSetupDTO getTicketSetup(Long seatId, Long roomId, Long sessionId) {
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

            @Override
            public List<TicketSetupDTO> getTicketSetups(BulkSaveTicketsDTO dto) {
                // Same hardening as getTicketSetup: surface a real 4xx; only outages fall back.
                RuntimeException clientError = errorTranslator.clientErrorOrNull(cause);
                if (clientError != null) {
                    throw clientError;
                }
                log.warn("catalog-service unavailable for bulk ticket-setup. Cause: {}", cause.toString());
                throw new BadRequestException("Catalog service is currently unavailable. Please try again later.");
            }
        };
    }
}
