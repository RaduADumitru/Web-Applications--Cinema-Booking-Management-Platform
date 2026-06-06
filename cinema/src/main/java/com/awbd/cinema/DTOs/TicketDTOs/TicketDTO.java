package com.awbd.cinema.DTOs.TicketDTOs;

import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.enums.TicketType;

import java.math.BigDecimal;

public record TicketDTO(
        Long id,
        boolean isAvailable,
        Long seatId,
        Long roomId,
        Long screenSessionId,
        TicketType type,
        BigDecimal price
) {
    public static TicketDTO from(Ticket t) {
        BigDecimal price = t.getTicketInfo() != null ? t.getTicketInfo().getPrice() : null;
        return new TicketDTO(
                t.getId(),
                t.isAvailable(),
                t.getSeat().getId(),
                t.getRoom().getId(),
                t.getScreenSession().getId(),
                t.getType(),
                price
        );
    }
}
