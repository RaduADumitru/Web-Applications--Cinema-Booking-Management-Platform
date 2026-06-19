package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.SeatDTOs.GenerateSeatsDTO;
import com.awbd.cinema.DTOs.SeatDTOs.SaveSeatDTO;
import com.awbd.cinema.DTOs.SeatDTOs.SeatDTO;
import com.awbd.cinema.services.SeatService.SeatService;
import com.awbd.cinema.utils.RestPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<SeatDTO> createSeat(@Valid @RequestBody SaveSeatDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(seatService.createSeat(dto));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<List<SeatDTO>> generateSeats(@Valid @RequestBody GenerateSeatsDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(seatService.generateSeats(dto));
    }

    @GetMapping
    public ResponseEntity<RestPage<SeatDTO>> getSeats(
            @RequestParam(required = false) String roomType,
            @RequestParam(required = false) Long screenSessionId,
            @RequestParam(required = false) Long movieId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(seatService.getSeats(roomType, screenSessionId, movieId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SeatDTO> getSeat(@PathVariable Long id) {
        return ResponseEntity.ok(seatService.getSeat(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<SeatDTO> updateSeat(@PathVariable Long id, @Valid @RequestBody SaveSeatDTO dto) {
        return ResponseEntity.ok(seatService.updateSeat(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<Void> deleteSeat(@PathVariable Long id) {
        seatService.deleteSeat(id);
        return ResponseEntity.noContent().build();
    }
}
