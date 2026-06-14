package com.awbd.cinema.DTOs.AuthDTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record LoginDTO(
        @NotEmpty(message = "The username field is required.")
        String username,
        @NotBlank(message = "The password field is required.")
        String password
) {
}
