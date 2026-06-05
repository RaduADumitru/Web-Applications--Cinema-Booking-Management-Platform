package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    boolean existsBySeatIdAndRoomIdAndScreenSessionId(Long seatId, Long roomId, Long sessionId);
    Page<Ticket> findByScreenSessionId(Long sessionId, Pageable pageable);
    Page<Ticket> findByRoomId(Long roomId, Pageable pageable);
    Page<Ticket> findByScreenSessionIdAndRoomId(Long sessionId, Long roomId, Pageable pageable);
    Page<Ticket> findByScreenSessionIdAndRoomIdAndIsAvailable(Long sessionId, Long roomId, boolean isAvailable, Pageable pageable);
    List<Ticket> findByScreenSessionIdAndOrderIsNotNull(Long sessionId); // used by scheduler — unbounded intentionally
}