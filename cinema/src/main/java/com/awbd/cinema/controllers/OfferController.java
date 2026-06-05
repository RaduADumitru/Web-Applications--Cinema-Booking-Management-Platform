package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.OfferDTOs.OfferDTO;
import com.awbd.cinema.DTOs.OfferDTOs.SaveOfferDTO;
import com.awbd.cinema.services.OfferService.OfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/offers")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offerService;

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<OfferDTO> createOffer(@Valid @RequestBody SaveOfferDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(offerService.createOffer(dto));
    }

    @GetMapping
    public ResponseEntity<List<OfferDTO>> getOffers() {
        return ResponseEntity.ok(offerService.getOffers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OfferDTO> getOffer(@PathVariable Long id) {
        return ResponseEntity.ok(offerService.getOffer(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<OfferDTO> updateOffer(
            @PathVariable Long id,
            @Valid @RequestBody SaveOfferDTO dto) {
        return ResponseEntity.ok(offerService.updateOffer(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<Void> deleteOffer(@PathVariable Long id) {
        offerService.deleteOffer(id);
        return ResponseEntity.noContent().build();
    }
}
