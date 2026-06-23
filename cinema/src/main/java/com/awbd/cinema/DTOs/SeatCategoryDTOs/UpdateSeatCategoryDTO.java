package com.awbd.cinema.DTOs.SeatCategoryDTOs;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record UpdateSeatCategoryDTO(
        @NotNull @PositiveOrZero BigDecimal extraFee,
        @NotNull @PositiveOrZero Integer extraPoints
) {}
