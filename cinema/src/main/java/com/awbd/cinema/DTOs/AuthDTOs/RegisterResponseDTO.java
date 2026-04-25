package com.awbd.cinema.DTOs.AuthDTOs;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;


public record RegisterResponseDTO(
        String message,
        String username
) {
}
