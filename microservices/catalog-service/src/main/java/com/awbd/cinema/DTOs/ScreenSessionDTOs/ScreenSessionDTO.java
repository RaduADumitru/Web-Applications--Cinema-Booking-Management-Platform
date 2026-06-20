package com.awbd.cinema.DTOs.ScreenSessionDTOs;

import com.awbd.cinema.entities.ScreenSession;
import com.awbd.cinema.enums.Format;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ScreenSessionDTO(
        Long id,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Long movieId,
        String movieTitle,
        Long sessionInfoId,
        Format format,
        Integer points,
        List<Long> roomIds
) {
    public static ScreenSessionDTO from(ScreenSession s) {
        Long sessionInfoId = s.getSessionInfo() != null ? s.getSessionInfo().getId() : null;
        Format format = s.getSessionInfo() != null ? s.getSessionInfo().getFormat() : null;
        Integer points = s.getSessionInfo() != null ? s.getSessionInfo().getPoints() : null;
        // The room(s) this session is scheduled in (a Room<->ScreenSession is many-to-many).
        // Exposed so clients can keep room/session selections consistent — a session can only be
        // allocated tickets for a room it actually runs in.
        List<Long> roomIds = s.getRooms() == null ? List.of()
                : s.getRooms().stream().map(r -> r.getId()).toList();
        return new ScreenSessionDTO(
                s.getId(),
                s.getDate(),
                s.getStartTime(),
                s.getEndTime(),
                s.getMovie().getId(),
                s.getMovie().getTitle(),
                sessionInfoId,
                format,
                points,
                roomIds
        );
    }
}
