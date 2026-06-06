package com.awbd.cinema.DTOs.TicketDTOs;

import jakarta.validation.constraints.NotNull;

public record SaveTicketDTO(
        @NotNull(message = "The seat ID is required.") Long seatId,
        @NotNull(message = "The room ID is required.") Long roomId,
        @NotNull(message = "The screen session ID is required.") Long screenSessionId
) {}
