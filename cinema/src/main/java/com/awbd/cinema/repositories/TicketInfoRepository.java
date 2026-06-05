package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.TicketInfo;
import com.awbd.cinema.enums.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketInfoRepository extends JpaRepository<TicketInfo, Long> {
    Optional<TicketInfo> findByType(TicketType type);
}
