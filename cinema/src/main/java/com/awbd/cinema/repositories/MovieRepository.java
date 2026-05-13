package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {
}
