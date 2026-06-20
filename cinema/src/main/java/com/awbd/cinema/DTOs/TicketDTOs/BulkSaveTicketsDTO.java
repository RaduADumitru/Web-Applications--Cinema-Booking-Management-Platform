package com.awbd.cinema.DTOs.TicketDTOs;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BulkSaveTicketsDTO(
        @NotEmpty(message = "The list of seat IDs cannot be empty.") List<Long> seatIds,
        @NotNull(message = "The room ID is required.") Long roomId,
        @NotNull(message = "The screen session ID is required.") Long screenSessionId
) {}
