package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.entities.Movie;
import com.awbd.cinema.entities.Room;
import com.awbd.cinema.entities.ScreenSession;
import com.awbd.cinema.entities.Seat;
import com.awbd.cinema.entities.SeatCategory;
import com.awbd.cinema.entities.SessionInfo;
import com.awbd.cinema.enums.SeatZone;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.RoomRepository;
import com.awbd.cinema.repositories.ScreenSessionRepository;
import com.awbd.cinema.repositories.SeatRepository;
import com.awbd.cinema.services.TicketSetupService.TicketSetupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketSetupServiceTest {

    @Mock private SeatRepository seatRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private ScreenSessionRepository screenSessionRepository;

    @InjectMocks private TicketSetupServiceImpl ticketSetupService;

    private Seat seat;
    private Room room;
    private ScreenSession session;

    @BeforeEach
    void setUp() {
        SeatCategory category = SeatCategory.builder()
                .extraFee(new BigDecimal("5.00"))
                .extraPoints(3)
                .build();
        seat = Seat.builder()
                .id(1L).row(4).number(7).zone(SeatZone.VIP).category(category)
                .build();
        room = Room.builder().id(2L).build();
        Movie movie = Movie.builder().id(10L).title("Inception").build();
        SessionInfo sessionInfo = SessionInfo.builder().points(8).build();
        session = ScreenSession.builder()
                .id(3L).date(LocalDate.of(2026, 7, 1)).startTime(LocalTime.of(19, 30))
                .movie(movie).sessionInfo(sessionInfo)
                .build();
    }

    @Test
    void getTicketSetup_ReturnsSnapshot_WhenValid() {
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(screenSessionRepository.findActiveById(3L)).thenReturn(Optional.of(session));
        when(roomRepository.existsByIdAndSeatsId(2L, 1L)).thenReturn(true);
        when(roomRepository.existsByIdAndScreenSessionsId(2L, 3L)).thenReturn(true);

        TicketSetupDTO dto = ticketSetupService.getTicketSetup(1L, 2L, 3L);

        assertEquals(4, dto.seatRow());
        assertEquals(7, dto.seatNumber());
        assertEquals("VIP", dto.seatZone());
        assertEquals(new BigDecimal("5.00"), dto.extraFee());
        assertEquals(3, dto.extraPoints());
        assertEquals("Inception", dto.movieTitle());
        assertEquals(LocalDate.of(2026, 7, 1), dto.sessionDate());
        assertEquals(LocalTime.of(19, 30), dto.sessionStartTime());
        assertEquals(8, dto.sessionPoints());
    }

    @Test
    void getTicketSetup_DefaultsFeeAndPoints_WhenNoCategoryOrSessionInfo() {
        seat.setCategory(null);
        session.setSessionInfo(null);
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(screenSessionRepository.findActiveById(3L)).thenReturn(Optional.of(session));
        when(roomRepository.existsByIdAndSeatsId(2L, 1L)).thenReturn(true);
        when(roomRepository.existsByIdAndScreenSessionsId(2L, 3L)).thenReturn(true);

        TicketSetupDTO dto = ticketSetupService.getTicketSetup(1L, 2L, 3L);

        assertEquals(BigDecimal.ZERO, dto.extraFee());
        assertEquals(0, dto.extraPoints());
        assertEquals(0, dto.sessionPoints());
    }

    @Test
    void getTicketSetup_ThrowsNotFound_WhenSeatMissing() {
        when(seatRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> ticketSetupService.getTicketSetup(1L, 2L, 3L));
    }

    @Test
    void getTicketSetup_ThrowsNotFound_WhenRoomMissing() {
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(roomRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> ticketSetupService.getTicketSetup(1L, 2L, 3L));
    }

    @Test
    void getTicketSetup_ThrowsNotFound_WhenSessionMissing() {
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(screenSessionRepository.findActiveById(3L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> ticketSetupService.getTicketSetup(1L, 2L, 3L));
    }

    @Test
    void getTicketSetup_ThrowsBadRequest_WhenSeatNotInRoom() {
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(screenSessionRepository.findActiveById(3L)).thenReturn(Optional.of(session));
        when(roomRepository.existsByIdAndSeatsId(2L, 1L)).thenReturn(false);
        assertThrows(BadRequestException.class, () -> ticketSetupService.getTicketSetup(1L, 2L, 3L));
    }

    @Test
    void getTicketSetup_ThrowsBadRequest_WhenSessionNotInRoom() {
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(screenSessionRepository.findActiveById(3L)).thenReturn(Optional.of(session));
        when(roomRepository.existsByIdAndSeatsId(2L, 1L)).thenReturn(true);
        when(roomRepository.existsByIdAndScreenSessionsId(2L, 3L)).thenReturn(false);
        assertThrows(BadRequestException.class, () -> ticketSetupService.getTicketSetup(1L, 2L, 3L));
    }

    @Test
    void getTicketSetups_ReturnsSnapshots_WhenValid() {
        com.awbd.cinema.DTOs.TicketDTOs.BulkSaveTicketsDTO bulkSaveDto =
                new com.awbd.cinema.DTOs.TicketDTOs.BulkSaveTicketsDTO(java.util.List.of(1L), 2L, 3L);

        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(screenSessionRepository.findActiveById(3L)).thenReturn(Optional.of(session));
        when(roomRepository.existsByIdAndScreenSessionsId(2L, 3L)).thenReturn(true);
        when(seatRepository.findAllById(java.util.List.of(1L))).thenReturn(java.util.List.of(seat));
        when(seatRepository.findByRoomIdAndSeatIds(2L, java.util.List.of(1L))).thenReturn(java.util.List.of(seat));

        java.util.List<TicketSetupDTO> dtos = ticketSetupService.getTicketSetups(bulkSaveDto);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
        assertEquals(4, dtos.get(0).seatRow());
    }

    @Test
    void getTicketSetups_ThrowsNotFound_WhenSeatMissing() {
        com.awbd.cinema.DTOs.TicketDTOs.BulkSaveTicketsDTO bulkSaveDto =
                new com.awbd.cinema.DTOs.TicketDTOs.BulkSaveTicketsDTO(java.util.List.of(1L, 99L), 2L, 3L);

        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));
        when(screenSessionRepository.findActiveById(3L)).thenReturn(Optional.of(session));
        when(roomRepository.existsByIdAndScreenSessionsId(2L, 3L)).thenReturn(true);
        when(seatRepository.findAllById(java.util.List.of(1L, 99L))).thenReturn(java.util.List.of(seat)); // only returns 1 seat

        assertThrows(NotFoundException.class, () -> ticketSetupService.getTicketSetups(bulkSaveDto));
    }
}
