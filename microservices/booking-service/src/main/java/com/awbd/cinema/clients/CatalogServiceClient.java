package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.TicketDTOs.BulkSaveTicketsDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "catalog-service",
        path = "/api/v1",
        fallbackFactory = CatalogServiceClientFallbackFactory.class
)
public interface CatalogServiceClient {

    @GetMapping("/internal/ticket-setup")
    TicketSetupDTO getTicketSetup(
            @RequestParam Long seatId,
            @RequestParam Long roomId,
            @RequestParam Long sessionId);

    @PostMapping("/internal/ticket-setup/bulk")
    List<TicketSetupDTO> getTicketSetups(@RequestBody BulkSaveTicketsDTO dto);
}

