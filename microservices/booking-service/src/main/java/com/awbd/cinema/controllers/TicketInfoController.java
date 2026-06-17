package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.TicketInfoDTOs.SaveTicketInfoDTO;
import com.awbd.cinema.DTOs.TicketInfoDTOs.TicketInfoDTO;
import com.awbd.cinema.services.TicketInfoService.TicketInfoService;
import com.awbd.cinema.utils.RestPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ticket-info")
@RequiredArgsConstructor
public class TicketInfoController {

    private final TicketInfoService ticketInfoService;

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<TicketInfoDTO> createTicketInfo(@Valid @RequestBody SaveTicketInfoDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketInfoService.createTicketInfo(dto));
    }

    @GetMapping
    public ResponseEntity<RestPage<TicketInfoDTO>> getTicketInfos() {
        return ResponseEntity.ok(ticketInfoService.getTicketInfos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketInfoDTO> getTicketInfo(@PathVariable Long id) {
        return ResponseEntity.ok(ticketInfoService.getTicketInfo(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<TicketInfoDTO> updateTicketInfo(@PathVariable Long id, @Valid @RequestBody SaveTicketInfoDTO dto) {
        return ResponseEntity.ok(ticketInfoService.updateTicketInfo(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<Void> deleteTicketInfo(@PathVariable Long id) {
        ticketInfoService.deleteTicketInfo(id);
        return ResponseEntity.noContent().build();
    }
}
