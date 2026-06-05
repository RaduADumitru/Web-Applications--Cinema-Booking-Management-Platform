package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.TicketDTOs.BookTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.SaveTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketDTO;
import com.awbd.cinema.services.TicketService.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<TicketDTO> createTicket(@Valid @RequestBody SaveTicketDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.createTicket(dto));
    }

    @GetMapping
    public ResponseEntity<List<TicketDTO>> getTickets(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Boolean isAvailable
    ) {
        return ResponseEntity.ok(ticketService.getTickets(sessionId, roomId, isAvailable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDTO> getTicket(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getTicket(id));
    }

    @PatchMapping("/{id}/book")
    public ResponseEntity<TicketDTO> bookTicket(@PathVariable Long id, @Valid @RequestBody BookTicketDTO dto) {
        return ResponseEntity.ok(ticketService.bookTicket(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }
}
