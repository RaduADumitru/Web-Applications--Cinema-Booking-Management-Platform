package com.awbd.cinema.DTOs.ScreenSessionDTOs;

import com.awbd.cinema.entities.Movie;
import com.awbd.cinema.entities.Room;
import com.awbd.cinema.entities.ScreenSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenSessionDTOTest {

    private ScreenSession.ScreenSessionBuilder baseSession() {
        return ScreenSession.builder()
                .id(3L)
                .date(LocalDate.of(2026, 7, 1))
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(20, 0))
                .movie(Movie.builder().id(10L).title("Inception").build());
    }

    @Test
    void from_exposesTheSessionsRoomIds() {
        ScreenSession session = baseSession()
                .rooms(List.of(Room.builder().id(7L).build()))
                .build();

        assertThat(ScreenSessionDTO.from(session).roomIds()).containsExactly(7L);
    }

    @Test
    void from_roomIdsIsEmpty_whenSessionHasNoRoom() {
        ScreenSession session = baseSession().rooms(List.of()).build();

        assertThat(ScreenSessionDTO.from(session).roomIds()).isEmpty();
    }
}
