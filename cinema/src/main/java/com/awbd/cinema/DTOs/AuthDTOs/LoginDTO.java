package com.awbd.cinema.DTOs.AuthDTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record LoginDTO(
        @NotEmpty(message = "Numele de utilizator nu poate fi gol.")
        String username,
        @NotBlank(message = "Parola nu poate fi gol.")
        String password
) {
}
