package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.Offer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long> {
    Optional<Offer> findByDay(DayOfWeek day);
    boolean existsByDay(DayOfWeek day);
}
