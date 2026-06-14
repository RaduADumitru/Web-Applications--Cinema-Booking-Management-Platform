package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.Ticket;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    boolean existsBySeatIdAndRoomIdAndScreenSessionId(Long seatId, Long roomId, Long sessionId);
    Page<Ticket> findByScreenSessionId(Long sessionId, Pageable pageable);
    Page<Ticket> findByRoomId(Long roomId, Pageable pageable);
    Page<Ticket> findByScreenSessionIdAndRoomId(Long sessionId, Long roomId, Pageable pageable);
    Page<Ticket> findByScreenSessionIdAndRoomIdAndIsAvailable(Long sessionId, Long roomId, boolean isAvailable, Pageable pageable);
    List<Ticket> findByScreenSessionIdAndOrderIsNotNull(Long sessionId);
    List<Ticket> findBySessionDateAndOrderIsNotNull(LocalDate sessionDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.id = :id")
    Optional<Ticket> findByIdForBooking(@Param("id") Long id);
}
