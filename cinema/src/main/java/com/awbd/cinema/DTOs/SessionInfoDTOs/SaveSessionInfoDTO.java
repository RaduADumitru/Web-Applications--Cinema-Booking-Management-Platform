package com.awbd.cinema.DTOs.SessionInfoDTOs;

import com.awbd.cinema.enums.Format;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SaveSessionInfoDTO(
        @NotNull(message = "The format is required.") Format format,
        @NotNull(message = "The points value is required.")
        @PositiveOrZero(message = "Points must be a non-negative number.") Integer points
) {}
