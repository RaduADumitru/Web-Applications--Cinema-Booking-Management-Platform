package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.ScreenSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScreenSessionRepository extends JpaRepository<ScreenSession, Long>, JpaSpecificationExecutor<ScreenSession> {
    Page<ScreenSession> findByMovieId(Long movieId, Pageable pageable);
    List<ScreenSession> findByDate(LocalDate date); // used by scheduler — unbounded intentionally

    // The session only if its movie is active (not soft-deleted). The INNER join + explicit
    // deletedAt IS NULL excludes sessions of a hidden movie, so callers can reject them (404)
    // instead of failing while resolving the @SQLRestriction-filtered lazy Movie proxy.
    @Query("select s from ScreenSession s join s.movie m where s.id = :id and m.deletedAt is null")
    Optional<ScreenSession> findActiveById(@Param("id") Long id);
}
