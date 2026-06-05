package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.Genre;
import com.awbd.cinema.enums.GenreType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {
    Optional<Genre> findByType(GenreType type);
}
