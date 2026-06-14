package com.awbd.cinema.DTOs.ScreenSessionDTOs;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record SaveScreenSessionDTO(
        @NotNull(message = "The movie ID is required.") Long movieId,
        @NotNull(message = "The date is required.") LocalDate date,
        @NotNull(message = "The start time is required.") LocalTime startTime,
        @NotNull(message = "The end time is required.") LocalTime endTime,
        Long sessionInfoId,
        @NotNull(message = "The room ID is required.") Long roomId
) {}
