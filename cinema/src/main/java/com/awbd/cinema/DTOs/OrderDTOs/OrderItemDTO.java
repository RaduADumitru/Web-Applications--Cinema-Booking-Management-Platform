package com.awbd.cinema.DTOs.OrderDTOs;

import com.awbd.cinema.enums.TicketType;
import jakarta.validation.constraints.NotNull;

public record OrderItemDTO(
        @NotNull(message = "The ticket ID is required.") Long ticketId,
        @NotNull(message = "The ticket type is required.") TicketType type
) {}
