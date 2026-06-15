package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "catalog-service",
        path = "/api/v1",
        fallback = CatalogServiceClientFallback.class
)
public interface CatalogServiceClient {

    @GetMapping("/internal/ticket-setup")
    TicketSetupDTO getTicketSetup(
            @RequestParam Long seatId,
            @RequestParam Long roomId,
            @RequestParam Long sessionId);
}
