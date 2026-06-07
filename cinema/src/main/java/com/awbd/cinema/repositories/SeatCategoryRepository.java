package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.SeatCategory;
import com.awbd.cinema.enums.SeatCategoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeatCategoryRepository extends JpaRepository<SeatCategory, Long> {
    Optional<SeatCategory> findByType(SeatCategoryType type);
}
