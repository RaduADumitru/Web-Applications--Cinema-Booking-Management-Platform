package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.services.UserService.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/{id}/loyalty-points")
    public ResponseEntity<LoyaltyPointsDTO> getLoyaltyPoints(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getLoyaltyPoints(id));
    }

    @PatchMapping("/{id}/loyalty-points")
    public ResponseEntity<LoyaltyPointsDTO> updateLoyaltyPoints(
            @PathVariable Long id, @Valid @RequestBody AdjustLoyaltyPointsDTO dto) {
        return ResponseEntity.ok(userService.updateLoyaltyPoints(id, dto));
    }
}
