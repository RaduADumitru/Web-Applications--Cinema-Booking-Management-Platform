package com.awbd.cinema.DTOs.SeatDTOs;

import com.awbd.cinema.enums.SeatZone;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GenerateSeatsDTO(
        @NotNull(message = "The room ID is required.")
        Long roomId,

        @NotNull(message = "The row count is required.")
        @Positive(message = "The row count must be a positive number.")
        Integer rows,

        @NotNull(message = "The seats per row count is required.")
        @Positive(message = "The seats per row count must be a positive number.")
        Integer seatsPerRow,

        @NotNull(message = "The starting row is required.")
        @Positive(message = "The starting row must be a positive number.")
        Integer startRow,

        @NotNull(message = "The starting seat number is required.")
        @Positive(message = "The starting seat number must be a positive number.")
        Integer startSeatNumber,

        @NotNull(message = "The zone is required.")
        SeatZone zone,

        Long categoryId
) {}
