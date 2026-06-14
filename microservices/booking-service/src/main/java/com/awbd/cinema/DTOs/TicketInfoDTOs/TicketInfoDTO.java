package com.awbd.cinema.DTOs.TicketInfoDTOs;

import com.awbd.cinema.entities.TicketInfo;
import com.awbd.cinema.enums.TicketType;

import java.math.BigDecimal;

public record TicketInfoDTO(
        Long id,
        TicketType type,
        BigDecimal price
) {
    public static TicketInfoDTO from(TicketInfo t) {
        return new TicketInfoDTO(t.getId(), t.getType(), t.getPrice());
    }
}
