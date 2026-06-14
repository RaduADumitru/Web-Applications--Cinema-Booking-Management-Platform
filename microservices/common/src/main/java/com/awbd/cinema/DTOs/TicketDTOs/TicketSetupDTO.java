package com.awbd.cinema.DTOs.TicketDTOs;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record TicketSetupDTO(
        Integer seatRow,
        Integer seatNumber,
        String seatZone,
        BigDecimal extraFee,
        Integer extraPoints,
        String movieTitle,
        LocalDate sessionDate,
        LocalTime sessionStartTime,
        Integer sessionPoints
) {
}
