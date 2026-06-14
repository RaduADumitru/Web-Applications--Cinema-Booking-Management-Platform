package com.awbd.cinema.DTOs.TicketDTOs;

import com.awbd.cinema.enums.TicketType;
import jakarta.validation.constraints.NotNull;

public record BookTicketDTO(
        @NotNull(message = "The ticket type is required.") TicketType type
) {}
