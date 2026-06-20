package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.TicketDTOs.BulkSaveTicketsDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.services.TicketSetupService.TicketSetupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping("/ticket-setup/bulk")
    public ResponseEntity<List<TicketSetupDTO>> ticketSetups(@Valid @RequestBody BulkSaveTicketsDTO dto) {
        return ResponseEntity.ok(ticketSetupService.getTicketSetups(dto));
    }
}
