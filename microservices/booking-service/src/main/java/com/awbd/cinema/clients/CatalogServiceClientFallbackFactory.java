package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.exceptions.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CatalogServiceClientFallbackFactory implements FallbackFactory<CatalogServiceClient> {

    @Override
    public CatalogServiceClient create(Throwable cause) {
        return new CatalogServiceClient() {
            @Override
            public TicketSetupDTO getTicketSetup(Long seatId, Long roomId, Long sessionId) {
                log.warn("catalog-service unavailable for ticket-setup (seat={}, room={}, session={}). Cause: {}",
                        seatId, roomId, sessionId, cause.toString());
                throw new BadRequestException("Catalog service is currently unavailable. Please try again later.");
            }
        };
    }
}
