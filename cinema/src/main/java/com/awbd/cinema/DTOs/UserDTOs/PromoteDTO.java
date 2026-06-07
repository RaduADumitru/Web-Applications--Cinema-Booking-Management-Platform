package com.awbd.cinema.DTOs.UserDTOs;

import com.awbd.cinema.enums.Role;
import jakarta.validation.constraints.PositiveOrZero;

public record PromoteDTO(
        @PositiveOrZero(message = "The id field must be a positive or zero number.")
        Long id,
        Role role
) {
}
