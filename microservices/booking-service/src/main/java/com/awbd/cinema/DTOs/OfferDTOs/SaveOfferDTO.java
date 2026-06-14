package com.awbd.cinema.DTOs.OfferDTOs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;

public record SaveOfferDTO(
        @NotNull(message = "Day is required.")
        DayOfWeek day,

        @NotNull(message = "Percent is required.")
        @Min(value = 1, message = "Percent must be at least 1.")
        @Max(value = 100, message = "Percent must be at most 100.")
        Integer percent
) {}
