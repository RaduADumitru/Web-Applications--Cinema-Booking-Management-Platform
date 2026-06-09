package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.ScreenSessionDTOs.SaveScreenSessionDTO;
import com.awbd.cinema.DTOs.ScreenSessionDTOs.ScreenSessionDTO;
import com.awbd.cinema.entities.Movie;
import com.awbd.cinema.entities.Room;
import com.awbd.cinema.entities.ScreenSession;
import com.awbd.cinema.entities.SessionInfo;
import com.awbd.cinema.enums.Format;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.MovieRepository;
import com.awbd.cinema.repositories.RoomRepository;
import com.awbd.cinema.repositories.ScreenSessionRepository;
import com.awbd.cinema.repositories.SessionInfoRepository;
import com.awbd.cinema.services.ScreenSessionService.ScreenSessionServiceImpl;
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
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScreenSessionServiceTest {

    @Mock private ScreenSessionRepository screenSessionRepository;
    @Mock private MovieRepository movieRepository;
    @Mock private SessionInfoRepository sessionInfoRepository;
    @Mock private RoomRepository roomRepository;

    @InjectMocks
    private ScreenSessionServiceImpl screenSessionService;

    private Movie sampleMovie;
    private Room sampleRoom;
    private SessionInfo sampleSessionInfo;
    private ScreenSession sampleSession;
    private SaveScreenSessionDTO saveDto;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        sampleMovie = Movie.builder().id(500L).title("Inception").build();
        sampleRoom = Room.builder().id(10L).name("IMAX Hall").screenSessions(new ArrayList<>()).build();
        sampleSessionInfo = SessionInfo.builder().id(30L).format(Format.THREE_D).points(15).build();

        sampleSession = ScreenSession.builder()
                .id(1L)
                .movie(sampleMovie)
                .date(LocalDate.of(2026, 7, 1))
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(20, 30))
                .sessionInfo(sampleSessionInfo)
                .build();

        // Layout: movieId, date, startTime, endTime, sessionInfoId, roomId
        saveDto = new SaveScreenSessionDTO(
                500L,
                LocalDate.of(2026, 7, 1),
                LocalTime.of(18, 0),
                LocalTime.of(20, 30),
                30L,
                10L
        );

        pageable = PageRequest.of(0, 20);
    }

    // ==========================================
    // CREATE SCREEN SESSION TESTS
    // ==========================================
    @Nested
    @DisplayName("createScreenSession Tests")
    class CreateScreenSessionTests {

        @Test
        @DisplayName("Should throw NotFoundException when target movie does not exist")
        void createScreenSession_MovieNotFound_ThrowsNotFoundException() {
            when(movieRepository.findById(500L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> screenSessionService.createScreenSession(saveDto))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Movie not found.");

            verify(screenSessionRepository, never()).save(any(ScreenSession.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when target room does not exist")
        void createScreenSession_RoomNotFound_ThrowsNotFoundException() {
            when(movieRepository.findById(500L)).thenReturn(Optional.of(sampleMovie));
            when(roomRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> screenSessionService.createScreenSession(saveDto))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Room not found.");
        }

        @Test
        @DisplayName("Should throw BadRequestException when end time occurs before or at start time")
        void createScreenSession_InvalidTimes_ThrowsBadRequestException() {
            SaveScreenSessionDTO invalidTimesDto = new SaveScreenSessionDTO(
                    500L, LocalDate.of(2026, 7, 1),
                    LocalTime.of(20, 0), LocalTime.of(18, 0), // 20:00 to 18:00 is invalid
                    30L, 10L
            );

            when(movieRepository.findById(500L)).thenReturn(Optional.of(sampleMovie));
            when(roomRepository.findById(10L)).thenReturn(Optional.of(sampleRoom));

            assertThatThrownBy(() -> screenSessionService.createScreenSession(invalidTimesDto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("End time must be after start time.");
        }

        @Test
        @DisplayName("Should build entity, link context relations, and persist safely")
        void createScreenSession_Success() {
            when(movieRepository.findById(500L)).thenReturn(Optional.of(sampleMovie));
            when(roomRepository.findById(10L)).thenReturn(Optional.of(sampleRoom));
            when(sessionInfoRepository.findById(30L)).thenReturn(Optional.of(sampleSessionInfo));
            when(screenSessionRepository.save(any(ScreenSession.class))).thenReturn(sampleSession);

            ScreenSessionDTO result = screenSessionService.createScreenSession(saveDto);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.movieTitle()).isEqualTo("Inception");
            assertThat(sampleRoom.getScreenSessions()).contains(sampleSession);
            verify(roomRepository, times(1)).save(sampleRoom);
        }
    }

    // ==========================================
    // READ OPERATIONS & FILTER SPECIFICATIONS
    // ==========================================
    @Nested
    @DisplayName("Query / Search Read Tests")
    class ReadScreenSessionTests {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Should find screen sessions matching complex dynamic specifications layout")
        void getScreenSessions_WithSpecifications_ReturnsPage() {
            Page<ScreenSession> page = new PageImpl<>(List.of(sampleSession));
            when(screenSessionRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            Page<ScreenSessionDTO> result = screenSessionService.getScreenSessions(500L, "THREE_D", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should throw NotFoundException when pulling movie session history for missing movie ID")
        void getScreenSessionsByMovie_MovieNotFound_ThrowsNotFoundException() {
            when(movieRepository.existsById(500L)).thenReturn(false);

            assertThatThrownBy(() -> screenSessionService.getScreenSessionsByMovie(500L, pageable))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Should find structural records linked to valid movie identifier index")
        void getScreenSessionsByMovie_ValidId_ReturnsPage() {
            Page<ScreenSession> page = new PageImpl<>(List.of(sampleSession));
            when(movieRepository.existsById(500L)).thenReturn(true);
            when(screenSessionRepository.findByMovieId(500L, pageable)).thenReturn(page);

            Page<ScreenSessionDTO> result = screenSessionService.getScreenSessionsByMovie(500L, pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(screenSessionRepository).findByMovieId(500L, pageable);
        }

        @Test
        @DisplayName("Should pull specific matching session elements if valid record ID is found")
        void getScreenSession_ValidId_ReturnsDTO() {
            when(screenSessionRepository.findById(1L)).thenReturn(Optional.of(sampleSession));

            ScreenSessionDTO result = screenSessionService.getScreenSession(1L);

            assertThat(result.id()).isEqualTo(1L);
        }
    }

    // ==========================================
    // UPDATE SCREEN SESSION TESTS
    // ==========================================
    @Nested
    @DisplayName("updateScreenSession Tests")
    class UpdateScreenSessionTests {

        @Test
        @DisplayName("Should throw NotFoundException when targeted updating session reference does not exist")
        void updateScreenSession_SessionNotFound_ThrowsNotFoundException() {
            when(screenSessionRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> screenSessionService.updateScreenSession(1L, saveDto))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Should successfully adjust properties when references and times check out valid")
        void updateScreenSession_Success() {
            when(screenSessionRepository.findById(1L)).thenReturn(Optional.of(sampleSession));
            when(movieRepository.findById(500L)).thenReturn(Optional.of(sampleMovie));
            when(sessionInfoRepository.findById(30L)).thenReturn(Optional.of(sampleSessionInfo));
            when(screenSessionRepository.save(any(ScreenSession.class))).thenAnswer(inv -> inv.getArgument(0));

            ScreenSessionDTO result = screenSessionService.updateScreenSession(1L, saveDto);

            assertThat(result).isNotNull();
            verify(screenSessionRepository, times(1)).save(any(ScreenSession.class));
        }
    }

    // ==========================================
    // DELETE SCREEN SESSION TESTS
    // ==========================================
    @Nested
    @DisplayName("deleteScreenSession Tests")
    class DeleteScreenSessionTests {

        @Test
        @DisplayName("Should throw NotFoundException when running removal path against non-existent session ID")
        void deleteScreenSession_NotFound_ThrowsNotFoundException() {
            when(screenSessionRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> screenSessionService.deleteScreenSession(1L))
                    .isInstanceOf(NotFoundException.class);

            verify(screenSessionRepository, never()).delete(any(ScreenSession.class));
        }

        @Test
        @DisplayName("Should sever parent mapping dependencies and call core delete contract execution")
        void deleteScreenSession_Success() {
            when(screenSessionRepository.findById(1L)).thenReturn(Optional.of(sampleSession));

            screenSessionService.deleteScreenSession(1L);

            assertThat(sampleSession.getSessionInfo()).isNull();
            verify(screenSessionRepository, times(1)).delete(sampleSession);
        }
    }
}