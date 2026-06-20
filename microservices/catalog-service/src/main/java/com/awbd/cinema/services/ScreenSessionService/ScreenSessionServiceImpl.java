package com.awbd.cinema.services.ScreenSessionService;

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
import com.awbd.cinema.utils.RestPage;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScreenSessionServiceImpl implements ScreenSessionService {

    private final ScreenSessionRepository screenSessionRepository;
    private final MovieRepository movieRepository;
    private final SessionInfoRepository sessionInfoRepository;
    private final RoomRepository roomRepository;

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "screen_session_lists", allEntries = true),
            @CacheEvict(value = "movie_session_lists", allEntries = true)
    })
    public ScreenSessionDTO createScreenSession(SaveScreenSessionDTO dto) {
        Movie movie = movieRepository.findById(dto.movieId())
                .orElseThrow(() -> new NotFoundException("Movie not found."));

        Room room = roomRepository.findById(dto.roomId())
                .orElseThrow(() -> new NotFoundException("Room not found."));

        if (dto.endTime().isBefore(dto.startTime()) || dto.endTime().equals(dto.startTime())) {
            throw new BadRequestException("End time must be after start time.");
        }

        SessionInfo sessionInfo = resolveSessionInfo(dto.sessionInfoId());

        ScreenSession session = ScreenSession.builder()
                .movie(movie)
                .date(dto.date())
                .startTime(dto.startTime())
                .endTime(dto.endTime())
                .sessionInfo(sessionInfo)
                .build();

        ScreenSession savedSession = screenSessionRepository.save(session);
        room.getScreenSessions().add(savedSession);
        roomRepository.save(room);

        return ScreenSessionDTO.from(savedSession);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "screen_session_lists")
    public RestPage<ScreenSessionDTO> getScreenSessions(Long movieId, String format, Pageable pageable) {
        Specification<ScreenSession> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // A movie is soft-deleted via @SQLRestriction("deleted_at IS NULL"); its sessions must
            // be hidden alongside it (and reappear if it is restored), otherwise rendering them
            // fails when the lazy movie proxy cannot resolve the now-filtered row.
            predicates.add(cb.isNull(root.get("movie").get("deletedAt")));

            if (movieId != null) {
                predicates.add(cb.equal(root.get("movie").get("id"), movieId));
            }

            if (format != null && !format.isBlank()) {
                Format fmt = Format.valueOf(format.toUpperCase());
                Join<ScreenSession, SessionInfo> infoJoin = root.join("sessionInfo", JoinType.INNER);
                predicates.add(cb.equal(infoJoin.get("format"), fmt));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return new RestPage<>(screenSessionRepository.findAll(spec, pageable).map(ScreenSessionDTO::from));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "movie_session_lists")
    public RestPage<ScreenSessionDTO> getScreenSessionsByMovie(Long movieId, Pageable pageable) {
        if (!movieRepository.existsById(movieId)) {
            throw new NotFoundException("Movie not found.");
        }
        return new RestPage<>(screenSessionRepository.findByMovieId(movieId, pageable).map(ScreenSessionDTO::from));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "single_screen_sessions", key = "#id")
    public ScreenSessionDTO getScreenSession(Long id) {
        // Exclude sessions whose movie is soft-deleted: they are hidden with their movie, so a
        // direct lookup should 404 rather than fail resolving the filtered lazy movie proxy.
        // root.get("movie") is an INNER join, so isNull(deletedAt) reliably drops such sessions.
        Specification<ScreenSession> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("id"), id),
                cb.isNull(root.get("movie").get("deletedAt"))
        );
        return screenSessionRepository.findOne(spec)
                .map(ScreenSessionDTO::from)
                .orElseThrow(() -> new NotFoundException("Screen session not found."));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_screen_sessions", key = "#id"),
            @CacheEvict(value = "screen_session_lists", allEntries = true),
            @CacheEvict(value = "movie_session_lists", allEntries = true)
    })
    public ScreenSessionDTO updateScreenSession(Long id, SaveScreenSessionDTO dto) {
        ScreenSession session = screenSessionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Screen session not found."));

        Movie movie = movieRepository.findById(dto.movieId())
                .orElseThrow(() -> new NotFoundException("Movie not found."));

        if (dto.endTime().isBefore(dto.startTime()) || dto.endTime().equals(dto.startTime())) {
            throw new BadRequestException("End time must be after start time.");
        }

        session.setMovie(movie);
        session.setDate(dto.date());
        session.setStartTime(dto.startTime());
        session.setEndTime(dto.endTime());
        session.setSessionInfo(resolveSessionInfo(dto.sessionInfoId()));

        return ScreenSessionDTO.from(screenSessionRepository.save(session));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_screen_sessions", key = "#id"),
            @CacheEvict(value = "screen_session_lists", allEntries = true),
            @CacheEvict(value = "movie_session_lists", allEntries = true)
    })
    public void deleteScreenSession(Long id) {
        ScreenSession session = screenSessionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Screen session not found."));
        session.setSessionInfo(null);
        screenSessionRepository.delete(session);
    }

    private SessionInfo resolveSessionInfo(Long sessionInfoId) {
        if (sessionInfoId == null) return null;
        return sessionInfoRepository.findById(sessionInfoId)
                .orElseThrow(() -> new NotFoundException("Session info not found."));
    }
}
