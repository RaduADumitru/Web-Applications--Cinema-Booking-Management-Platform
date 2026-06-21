package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.SeatCategoryDTOs.SeatCategoryDTO;
import com.awbd.cinema.DTOs.SeatCategoryDTOs.UpdateSeatCategoryDTO;
import com.awbd.cinema.repositories.SeatCategoryRepository;
import com.awbd.cinema.exceptions.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/seat-categories")
@RequiredArgsConstructor
public class SeatCategoryController {

    private final SeatCategoryRepository seatCategoryRepository;

    @GetMapping
    public ResponseEntity<List<SeatCategoryDTO>> getSeatCategories() {
        List<SeatCategoryDTO> categories = seatCategoryRepository.findAll()
                .stream()
                .map(SeatCategoryDTO::from)
                .toList();
        return ResponseEntity.ok(categories);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<SeatCategoryDTO> updateSeatCategory(@PathVariable Long id, @Valid @RequestBody UpdateSeatCategoryDTO dto) {
        var category = seatCategoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Seat category not found."));
        category.setExtraFee(dto.extraFee());
        category.setExtraPoints(dto.extraPoints());
        return ResponseEntity.ok(SeatCategoryDTO.from(seatCategoryRepository.save(category)));
    }
}
