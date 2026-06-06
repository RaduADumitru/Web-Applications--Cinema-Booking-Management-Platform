package com.awbd.cinema.DTOs.SeatDTOs;

import com.awbd.cinema.enums.SeatZone;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SaveSeatDTO(
        @NotNull(message = "The row is required.")
        @Positive(message = "The row must be a positive number.")
        Integer row,

        @NotNull(message = "The seat number is required.")
        @Positive(message = "The seat number must be a positive number.")
        Integer number,

        @NotNull(message = "The zone is required.")
        SeatZone zone,

        Long categoryId,

        @NotNull(message = "The room ID is required.")
        Long roomId
) {}
