package com.awbd.cinema;

import com.awbd.cinema.DTOs.ScreenSessionDTOs.ScreenSessionDTO;
import com.awbd.cinema.entities.Movie;
import com.awbd.cinema.entities.ScreenSession;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.MovieRepository;
import com.awbd.cinema.repositories.ScreenSessionRepository;
import com.awbd.cinema.services.ScreenSessionService.ScreenSessionService;
import com.awbd.cinema.utils.RestPage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reproduces the bug where a screen session whose movie has been soft-deleted
 * ({@code @SQLRestriction("deleted_at IS NULL")} on {@link Movie}) blows up when the session is
 * rendered: {@code ScreenSessionDTO.from} initializes the lazy movie proxy, whose load query is
 * filtered by {@code deleted_at IS NULL}, finds no row, and throws ObjectNotFoundException -> 500.
 *
 * <p>Because the soft-delete is reversible, the sessions must be preserved but hidden while the
 * movie is hidden, not deleted.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScreenSessionIntegrationTest {

    @Autowired
    private ScreenSessionService screenSessionService;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ScreenSessionRepository screenSessionRepository;

    @PersistenceContext
    private EntityManager em;

    /**
     * Seeds a movie with one screen session, then soft-deletes the movie and clears the
     * persistence context so the movie is subsequently loaded as a fresh lazy proxy (the condition
     * that triggers the bug). Returns the session id.
     */
    private Long seedSessionForSoftDeletedMovie() {
        Movie movie = movieRepository.save(Movie.builder()
                .id(987654L)
                .title("Soft-Deleted Movie Fixture")
                .duration(120)
                .description("fixture")
                .rating(7.0)
                .releaseDate(LocalDateTime.of(2024, 1, 1, 0, 0))
                .ageRating("PG-13")
                .genres(List.of())
                .build());

        ScreenSession session = screenSessionRepository.save(ScreenSession.builder()
                .date(LocalDate.now().plusDays(40))
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(20, 0))
                .movie(movie)
                .build());

        // Soft-delete the movie, mirroring MovieServiceImpl.deleteMovie.
        movie.setDeletedAt(LocalDateTime.now());
        em.flush();
        em.clear();

        return session.getId();
    }

    @Test
    void getScreenSessions_excludesSessionsWhoseMovieIsSoftDeleted() {
        Long sessionId = seedSessionForSoftDeletedMovie();

        RestPage<ScreenSessionDTO> page =
                screenSessionService.getScreenSessions(null, null, PageRequest.of(0, 50));

        assertThat(page.getContent()).noneMatch(dto -> dto.id().equals(sessionId));
    }

    @Test
    void getScreenSession_ofSoftDeletedMovie_throwsNotFound() {
        Long sessionId = seedSessionForSoftDeletedMovie();

        assertThatThrownBy(() -> screenSessionService.getScreenSession(sessionId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findActiveById_excludesSoftDeletedMovieSessions_returnsActiveOnes() {
        Long hiddenSessionId = seedSessionForSoftDeletedMovie();
        assertThat(screenSessionRepository.findActiveById(hiddenSessionId)).isEmpty();

        ScreenSession active = seedActiveSession(112233L, LocalDate.now().plusDays(60));
        assertThat(screenSessionRepository.findActiveById(active.getId())).isPresent();
    }

    @Test
    void findByDate_excludesSessionsWhoseMovieIsSoftDeleted() {
        LocalDate date = LocalDate.now().plusDays(40); // the date used by the soft-deleted fixture
        Long hiddenSessionId = seedSessionForSoftDeletedMovie();
        ScreenSession active = seedActiveSession(223344L, date);

        List<ScreenSession> onDate = screenSessionRepository.findByDate(date);

        assertThat(onDate).extracting(ScreenSession::getId)
                .contains(active.getId())
                .doesNotContain(hiddenSessionId);
    }

    private ScreenSession seedActiveSession(long movieId, LocalDate date) {
        Movie active = movieRepository.save(Movie.builder()
                .id(movieId)
                .title("Active Fixture " + movieId)
                .duration(100)
                .description("fixture")
                .rating(6.0)
                .releaseDate(LocalDateTime.of(2024, 2, 2, 0, 0))
                .ageRating("PG")
                .genres(List.of())
                .build());
        ScreenSession session = screenSessionRepository.save(ScreenSession.builder()
                .date(date)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .movie(active)
                .build());
        em.flush();
        em.clear();
        return session;
    }
}
