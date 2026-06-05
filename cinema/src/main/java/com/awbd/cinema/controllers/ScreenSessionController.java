package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.ScreenSessionDTOs.SaveScreenSessionDTO;
import com.awbd.cinema.DTOs.ScreenSessionDTOs.ScreenSessionDTO;
import com.awbd.cinema.services.ScreenSessionService.ScreenSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/screen-sessions")
@RequiredArgsConstructor
public class ScreenSessionController {

    private final ScreenSessionService screenSessionService;

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ScreenSessionDTO> createScreenSession(@Valid @RequestBody SaveScreenSessionDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(screenSessionService.createScreenSession(dto));
    }

    @GetMapping
    public ResponseEntity<List<ScreenSessionDTO>> getScreenSessions(
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) String format
    ) {
        return ResponseEntity.ok(screenSessionService.getScreenSessions(movieId, format));
    }

    @GetMapping("/movie/{movieId}")
    public ResponseEntity<List<ScreenSessionDTO>> getScreenSessionsByMovie(@PathVariable Long movieId) {
        return ResponseEntity.ok(screenSessionService.getScreenSessionsByMovie(movieId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScreenSessionDTO> getScreenSession(@PathVariable Long id) {
        return ResponseEntity.ok(screenSessionService.getScreenSession(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ScreenSessionDTO> updateScreenSession(@PathVariable Long id, @Valid @RequestBody SaveScreenSessionDTO dto) {
        return ResponseEntity.ok(screenSessionService.updateScreenSession(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<Void> deleteScreenSession(@PathVariable Long id) {
        screenSessionService.deleteScreenSession(id);
        return ResponseEntity.noContent().build();
    }
}
