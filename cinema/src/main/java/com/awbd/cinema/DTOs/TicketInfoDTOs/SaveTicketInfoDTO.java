package com.awbd.cinema.DTOs.TicketInfoDTOs;

import com.awbd.cinema.enums.TicketType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SaveTicketInfoDTO(
        @NotNull(message = "The ticket type is required.") TicketType type,
        @NotNull(message = "The price is required.")
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must be a non-negative number.")
        BigDecimal price
) {}
