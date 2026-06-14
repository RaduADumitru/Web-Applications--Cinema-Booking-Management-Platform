package com.awbd.cinema.DTOs.ScreenSessionDTOs;

import com.awbd.cinema.entities.ScreenSession;
import com.awbd.cinema.enums.Format;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScreenSessionDTO(
        Long id,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Long movieId,
        String movieTitle,
        Long sessionInfoId,
        Format format,
        Integer points
) {
    public static ScreenSessionDTO from(ScreenSession s) {
        Long sessionInfoId = s.getSessionInfo() != null ? s.getSessionInfo().getId() : null;
        Format format = s.getSessionInfo() != null ? s.getSessionInfo().getFormat() : null;
        Integer points = s.getSessionInfo() != null ? s.getSessionInfo().getPoints() : null;
        return new ScreenSessionDTO(
                s.getId(),
                s.getDate(),
                s.getStartTime(),
                s.getEndTime(),
                s.getMovie().getId(),
                s.getMovie().getTitle(),
                sessionInfoId,
                format,
                points
        );
    }
}
