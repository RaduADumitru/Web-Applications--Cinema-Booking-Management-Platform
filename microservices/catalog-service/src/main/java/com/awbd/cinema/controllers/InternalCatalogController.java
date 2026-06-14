package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.services.TicketSetupService.TicketSetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalCatalogController {

    private final TicketSetupService ticketSetupService;

    @GetMapping("/ticket-setup")
    public ResponseEntity<TicketSetupDTO> ticketSetup(
            @RequestParam Long seatId,
            @RequestParam Long roomId,
            @RequestParam Long sessionId) {
        return ResponseEntity.ok(ticketSetupService.getTicketSetup(seatId, roomId, sessionId));
    }
}
