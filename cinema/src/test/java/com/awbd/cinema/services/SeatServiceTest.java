package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.SeatDTOs.SaveSeatDTO;
import com.awbd.cinema.DTOs.SeatDTOs.SeatDTO;
import com.awbd.cinema.entities.Room;
import com.awbd.cinema.entities.ScreenSession;
import com.awbd.cinema.entities.Seat;
import com.awbd.cinema.entities.SeatCategory;
import com.awbd.cinema.enums.SeatCategoryType;
import com.awbd.cinema.enums.SeatZone;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.RoomRepository;
import com.awbd.cinema.repositories.SeatCategoryRepository;
import com.awbd.cinema.repositories.SeatRepository;
import com.awbd.cinema.services.SeatService.SeatServiceImpl;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock private SeatRepository seatRepository;
    @Mock private SeatCategoryRepository seatCategoryRepository;
    @Mock private RoomRepository roomRepository;

    @InjectMocks
    private SeatServiceImpl seatService;

    private Room sampleRoom;
    private Seat sampleSeat;
    private SeatCategory sampleCategory;
    private SaveSeatDTO saveSeatDto;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        sampleRoom = Room.builder()
                .id(10L)
                .name("Screen 1")
                .seats(new ArrayList<>())
                .build();

        sampleCategory = SeatCategory.builder()
                .id(5L)
                .type(SeatCategoryType.VIP)
                .build();

        sampleSeat = Seat.builder()
                .id(1L)
                .row(3)
                .number(14)
                .zone(SeatZone.VIP)
                .category(sampleCategory)
                .build();

        saveSeatDto = new SaveSeatDTO(3, 14, SeatZone.VIP, 10L, 5L);
        pageable = PageRequest.of(0, 20);
    }

    // ==========================================
    // CREATE SEAT TESTS
    // ==========================================
    @Nested
    @DisplayName("createSeat Tests")
    class CreateSeatTests {

        @Test
        @DisplayName("Should throw NotFoundException when parent room does not exist")
        void createSeat_RoomNotFound_ThrowsNotFoundException() {
            // Code will call roomRepository.findById(dto.roomId()) which is 5L
            when(roomRepository.findById(5L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> seatService.createSeat(saveSeatDto))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Room not found.");

            verify(seatRepository, never()).save(any(Seat.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when specified category id does not exist")
        void createSeat_CategoryNotFound_ThrowsNotFoundException() {
            // Room ID is 5L, Category ID is 10L
            when(roomRepository.findById(5L)).thenReturn(Optional.of(sampleRoom));
            when(seatCategoryRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> seatService.createSeat(saveSeatDto))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Seat category not found.");
        }

        @Test
        @DisplayName("Should save seat, attach it to the target room context, and return DTO")
        void createSeat_Success_WithCategory() {
            // Room ID is 5L, Category ID is 10L
            when(roomRepository.findById(5L)).thenReturn(Optional.of(sampleRoom));
            when(seatCategoryRepository.findById(10L)).thenReturn(Optional.of(sampleCategory));
            when(seatRepository.save(any(Seat.class))).thenReturn(sampleSeat);

            SeatDTO result = seatService.createSeat(saveSeatDto);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(sampleRoom.getSeats()).contains(sampleSeat);
            verify(roomRepository, times(1)).save(sampleRoom);
        }

        @Test
        @DisplayName("Should successfully save seat with null category details when categoryId is omitted")
        void createSeat_Success_NullCategory() {
            // To make category null, the 4th argument must be null.
            // The 5th argument (Room ID) must be 5L so the room lookup succeeds.
            SaveSeatDTO noCategoryDto = new SaveSeatDTO(3, 14, SeatZone.VIP, null, 5L);
            sampleSeat.setCategory(null);

            when(roomRepository.findById(5L)).thenReturn(Optional.of(sampleRoom));
            when(seatRepository.save(any(Seat.class))).thenReturn(sampleSeat);

            SeatDTO result = seatService.createSeat(noCategoryDto);

            assertThat(result).isNotNull();
            verify(seatCategoryRepository, never()).findById(anyLong());
        }
    }

    // ==========================================
    // READ SEAT & SPECIFICATION TESTS
    // ==========================================
    @Nested
    @DisplayName("getSeats & getSeat Tests")
    class ReadSeatTests {

        @Mock private Root<Seat> root;
        @Mock private CriteriaQuery<?> query;
        @Mock private CriteriaBuilder cb;
        @Mock private Join<Seat, Room> roomJoin;
        @Mock private Join<Room, ScreenSession> sessionJoin;
        @Mock private Path<Object> pathMock;

        @SuppressWarnings("unchecked")
        private Specification<Seat> captureSpecification(String roomType, Long screenSessionId, Long movieId) {
            ArgumentCaptor<Specification<Seat>> specCaptor = ArgumentCaptor.forClass(Specification.class);
            Page<Seat> seatPage = new PageImpl<>(List.of(sampleSeat));

            when(seatRepository.findAll(specCaptor.capture(), eq(pageable))).thenReturn(seatPage);

            seatService.getSeats(roomType, screenSessionId, movieId, pageable);

            return specCaptor.getValue();
        }

        @Test
        @DisplayName("Should execute all specification branches when all filters are provided")
        void getSeats_WithAllFilters_ExecutesAllSpecificationBranches() {
            Specification<Seat> spec = captureSpecification("IMAX", 1L, 2L);
            assertThat(spec).isNotNull();

            // 👇 FIX: Using doReturn().when() to bypass Java type-inference limitations
            doReturn(roomJoin).when(root).join(eq("rooms"), eq(JoinType.INNER));
            doReturn(sessionJoin).when(roomJoin).join(eq("screenSessions"), eq(JoinType.INNER));

            when(roomJoin.get("type")).thenReturn(pathMock);
            when(sessionJoin.get("id")).thenReturn(pathMock);

            Path<Object> moviePath = mock(Path.class);
            when(sessionJoin.get("movie")).thenReturn(moviePath);
            when(moviePath.get("id")).thenReturn(pathMock);

            spec.toPredicate(root, query, cb);

            verify(root).join("rooms", JoinType.INNER);
            verify(roomJoin).join("screenSessions", JoinType.INNER);
            verify(query).distinct(true);
            verify(cb).and(any(Predicate[].class));
        }

        @Test
        @DisplayName("Should bypass all inner joins and predicates when all filters are null")
        void getSeats_WithNoFilters_BypassesAllJoinsAndPredicates() {
            Specification<Seat> spec = captureSpecification(null, null, null);
            assertThat(spec).isNotNull();

            spec.toPredicate(root, query, cb);

            verify(root, never()).join(anyString(), any(JoinType.class));
            verify(query).distinct(true);
            verify(cb).and(any(Predicate[].class));
        }

        @Test
        @DisplayName("Should join only rooms table when only roomType filter is supplied")
        void getSeats_WithOnlyRoomTypeFilter_JoinsRoomOnly() {
            Specification<Seat> spec = captureSpecification("IMAX", null, null);
            assertThat(spec).isNotNull();

            // 👇 FIX: Applied doReturn().when() here as well
            doReturn(roomJoin).when(root).join(eq("rooms"), eq(JoinType.INNER));
            when(roomJoin.get("type")).thenReturn(pathMock);

            spec.toPredicate(root, query, cb);

            verify(root).join("rooms", JoinType.INNER);
            verify(roomJoin, never()).join(anyString(), any(JoinType.class));
        }

        @Test
        @DisplayName("Should join room and session tables when only movieId filter is supplied")
        void getSeats_WithOnlyMovieIdFilter_JoinsRoomAndSession() {
            Specification<Seat> spec = captureSpecification(null, null, 5L);
            assertThat(spec).isNotNull();

            // 👇 FIX: Applied doReturn().when() here as well
            doReturn(roomJoin).when(root).join(eq("rooms"), eq(JoinType.INNER));
            doReturn(sessionJoin).when(roomJoin).join(eq("screenSessions"), eq(JoinType.INNER));

            Path<Object> moviePath = mock(Path.class);
            when(sessionJoin.get("movie")).thenReturn(moviePath);
            when(moviePath.get("id")).thenReturn(pathMock);

            spec.toPredicate(root, query, cb);

            verify(root).join("rooms", JoinType.INNER);
            verify(roomJoin).join("screenSessions", JoinType.INNER);
        }

        @Test
        @DisplayName("Should return accurate DTO structure when looking up valid seat ID")
        void getSeat_ValidId_ReturnsSeatDTO() {
            when(seatRepository.findById(1L)).thenReturn(Optional.of(sampleSeat));

            SeatDTO result = seatService.getSeat(1L);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should throw NotFoundException when looking up a missing seat ID")
        void getSeat_NotFound_ThrowsNotFoundException() {
            when(seatRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> seatService.getSeat(99L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Seat not found.");
        }
    }

    // ==========================================
    // UPDATE SEAT TESTS
    // ==========================================
    @Nested
    @DisplayName("updateSeat Tests")
    class UpdateSeatTests {

        @Test
        @DisplayName("Should throw NotFoundException when targeted updating seat ID doesn't exist")
        void updateSeat_SeatNotFound_ThrowsNotFoundException() {
            when(seatRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> seatService.updateSeat(1L, saveSeatDto))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Should update fields and execute persistence save when payload is valid")
        void updateSeat_Success() {
            SaveSeatDTO updateDto = new SaveSeatDTO(5, 20, SeatZone.VIP, 10L, 5L);

            Seat updatedSeatEntity = Seat.builder()
                    .id(1L)
                    .row(5)
                    .number(20)
                    .zone(SeatZone.VIP)
                    .category(sampleCategory)
                    .build();

            when(seatRepository.findById(1L)).thenReturn(Optional.of(sampleSeat));
            when(seatCategoryRepository.findById(10L)).thenReturn(Optional.of(sampleCategory));
            when(seatRepository.save(any(Seat.class))).thenReturn(updatedSeatEntity);

            SeatDTO result = seatService.updateSeat(1L, updateDto);

            assertThat(result.row()).isEqualTo(5);
            assertThat(result.number()).isEqualTo(20);
        }
    }

    // ==========================================
    // DELETE SEAT TESTS
    // ==========================================
    @Nested
    @DisplayName("deleteSeat Tests")
    class DeleteSeatTests {

        @Test
        @DisplayName("Should trigger cascading database removal when targeting valid ID")
        void deleteSeat_ValidId_DeletesRecord() {
            when(seatRepository.existsById(1L)).thenReturn(true);

            seatService.deleteSeat(1L);

            verify(seatRepository, times(1)).deleteById(1L);
        }

        @Test
        @DisplayName("Should throw NotFoundException when running removal against a non-existent ID")
        void deleteSeat_NotFound_ThrowsNotFoundException() {
            when(seatRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> seatService.deleteSeat(99L))
                    .isInstanceOf(NotFoundException.class);
            verify(seatRepository, never()).deleteById(anyLong());
        }
    }
}