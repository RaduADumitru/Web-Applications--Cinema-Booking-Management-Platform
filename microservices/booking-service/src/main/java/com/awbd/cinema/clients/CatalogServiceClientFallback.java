package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.exceptions.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CatalogServiceClientFallback implements CatalogServiceClient {

    @Override
    public TicketSetupDTO getTicketSetup(Long seatId, Long roomId, Long sessionId) {
        log.warn("catalog-service unavailable for ticket-setup (seat={}, room={}, session={}).", seatId, roomId, sessionId);
        throw new BadRequestException("Catalog service is currently unavailable. Please try again later.");
    }
}
