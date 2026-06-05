package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.ScreenSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;

public interface ScreenSessionRepository extends JpaRepository<ScreenSession, Long>, JpaSpecificationExecutor<ScreenSession> {
    List<ScreenSession> findByMovieId(Long movieId);
    List<ScreenSession> findByDate(LocalDate date);
}
