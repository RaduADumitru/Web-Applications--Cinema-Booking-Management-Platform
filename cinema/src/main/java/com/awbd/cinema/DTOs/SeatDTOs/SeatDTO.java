package com.awbd.cinema.DTOs.SeatDTOs;

import com.awbd.cinema.entities.Seat;
import com.awbd.cinema.enums.SeatZone;

public record SeatDTO(
        Long id,
        Integer row,
        Integer number,
        SeatZone zone,
        Long categoryId
) {
    public static SeatDTO from(Seat s) {
        return new SeatDTO(
                s.getId(),
                s.getRow(),
                s.getNumber(),
                s.getZone(),
                s.getCategory() != null ? s.getCategory().getId() : null
        );
    }
}
