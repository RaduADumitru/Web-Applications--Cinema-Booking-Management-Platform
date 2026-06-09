package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.RoomDTOs.RoomDTO;
import com.awbd.cinema.DTOs.RoomDTOs.SaveRoomDTO;
import com.awbd.cinema.entities.Room;
import com.awbd.cinema.entities.ScreenSession;
import com.awbd.cinema.entities.Seat;
import com.awbd.cinema.enums.RoomType;
import com.awbd.cinema.enums.SeatZone;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.RoomRepository;
import com.awbd.cinema.repositories.ScreenSessionRepository;
import com.awbd.cinema.repositories.SeatRepository;
import com.awbd.cinema.services.RoomService.RoomServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private SeatRepository seatRepository;
    @Mock private ScreenSessionRepository screenSessionRepository;

    @InjectMocks
    private RoomServiceImpl roomService;

    private Room sampleRoom;
    private SaveRoomDTO saveRoomDto;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        sampleRoom = Room.builder()
                .id(1L)
                .name("Grand Arena")
                .type(RoomType.IMAX)
                .floor(2)
                .seats(new ArrayList<>())
                .screenSessions(new ArrayList<>())
                .build();

        saveRoomDto = new SaveRoomDTO(RoomType.IMAX, "Grand Arena", 2);
        pageable = PageRequest.of(0, 10);
    }

    // ==========================================
    // ROOM CRUD OPERATIONS
    // ==========================================
    @Nested
    @DisplayName("Room Core CRUD Tests")
    class RoomCrudTests {

        @Test
        @DisplayName("Should successfully create a new room configuration")
        void createRoom_Success() {
            when(roomRepository.save(any(Room.class))).thenReturn(sampleRoom);

            RoomDTO result = roomService.createRoom(saveRoomDto);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("Grand Arena");
            assertThat(result.floor()).isEqualTo(2);
            verify(roomRepository, times(1)).save(any(Room.class));
        }

        @Test
        @DisplayName("Should return a paginated collection of rooms")
        void getRooms_ReturnsPaginatedRooms() {
            Page<Room> roomPage = new PageImpl<>(List.of(sampleRoom));
            when(roomRepository.findAll(pageable)).thenReturn(roomPage);

            Page<RoomDTO> result = roomService.getRooms(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).name()).isEqualTo("Grand Arena");
        }

        @Test
        @DisplayName("Should fetch single room by ID if it exists")
        void getRoom_ValidId_ReturnsRoomDTO() {
            when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));

            RoomDTO result = roomService.getRoom(1L);

            assertThat(result.id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should throw NotFoundException when finding room by non-existent ID")
        void getRoom_NotFound_ThrowsNotFoundException() {
            when(roomRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> roomService.getRoom(99L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Room not found.");
        }

        @Test
        @DisplayName("Should successfully update room fields")
        void updateRoom_ValidId_UpdatesProperties() {
            SaveRoomDTO updateDto = new SaveRoomDTO(RoomType.IMAX, "IMAX Theater", 1);
            when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));
            when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

            RoomDTO result = roomService.updateRoom(1L, updateDto);

            assertThat(result.name()).isEqualTo("IMAX Theater");
            assertThat(result.type()).isEqualTo(RoomType.IMAX);
            assertThat(result.floor()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should delete room when valid ID is provided")
        void deleteRoom_ValidId_DeletesRecord() {
            when(roomRepository.existsById(1L)).thenReturn(true);

            roomService.deleteRoom(1L);

            verify(roomRepository, times(1)).deleteById(1L);
        }

        @Test
        @DisplayName("Should throw NotFoundException when trying to delete a non-existent room")
        void deleteRoom_NotFound_ThrowsNotFoundException() {
            when(roomRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> roomService.deleteRoom(99L))
                    .isInstanceOf(NotFoundException.class);
            verify(roomRepository, never()).deleteById(anyLong());
        }
    }

    // ==========================================
    // SEAT MANAGEMENT METHODS
    // ==========================================
    @Nested
    @DisplayName("Seat Association Tests")
    class SeatAssociationTests {

        private Seat sampleSeat;

        @BeforeEach
        void setupSeats() {
            sampleSeat = Seat.builder().id(50L).row(5).number(12).zone(SeatZone.VIP).build();
        }

        @Test
        @DisplayName("Should link seat to a room when no previous connection exists")
        void addSeat_Success() {
            when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));
            when(seatRepository.findById(50L)).thenReturn(Optional.of(sampleSeat));
            when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

            RoomDTO result = roomService.addSeat(1L, 50L);

            assertThat(sampleRoom.getSeats()).contains(sampleSeat);
            verify(roomRepository).save(sampleRoom);
        }

        @Test
        @DisplayName("Should throw BadRequestException when seat is already linked to the room")
        void addSeat_AlreadyExists_ThrowsBadRequestException() {
            sampleRoom.getSeats().add(sampleSeat);

            when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));
            when(seatRepository.findById(50L)).thenReturn(Optional.of(sampleSeat));

            assertThatThrownBy(() -> roomService.addSeat(1L, 50L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Seat is already in this room.");
        }

        @Test
        @DisplayName("Should break seat link on removal")
        void removeSeat_Success() {
            sampleRoom.getSeats().add(sampleSeat);
            when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));
            when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

            roomService.removeSeat(1L, 50L);

            assertThat(sampleRoom.getSeats()).isEmpty();
            verify(roomRepository).save(sampleRoom);
        }
    }

    // ==========================================
    // SCREEN SESSION MANAGEMENT METHODS
    // ==========================================
    @Nested
    @DisplayName("ScreenSession Association Tests")
    class ScreenSessionAssociationTests {

        private ScreenSession sampleSession;

        @BeforeEach
        void setupSessions() {
            sampleSession = ScreenSession.builder().id(200L).build();
        }

        @Test
        @DisplayName("Should link screen session to a room when no previous connection exists")
        void addScreenSession_Success() {
            when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));
            when(screenSessionRepository.findById(200L)).thenReturn(Optional.of(sampleSession));
            when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

            roomService.addScreenSession(1L, 200L);

            assertThat(sampleRoom.getScreenSessions()).contains(sampleSession);
            verify(roomRepository).save(sampleRoom);
        }

        @Test
        @DisplayName("Should throw BadRequestException when session is already linked to the room")
        void addScreenSession_AlreadyExists_ThrowsBadRequestException() {
            sampleRoom.getScreenSessions().add(sampleSession);

            when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));
            when(screenSessionRepository.findById(200L)).thenReturn(Optional.of(sampleSession));

            assertThatThrownBy(() -> roomService.addScreenSession(1L, 200L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Screen session is already in this room.");
        }

        @Test
        @DisplayName("Should break screen session link on removal")
        void removeScreenSession_Success() {
            sampleRoom.getScreenSessions().add(sampleSession);
            when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));
            when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

            roomService.removeScreenSession(1L, 200L);

            assertThat(sampleRoom.getScreenSessions()).isEmpty();
            verify(roomRepository).save(sampleRoom);
        }
    }
}