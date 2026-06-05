package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    boolean existsBySeatIdAndRoomIdAndScreenSessionId(Long seatId, Long roomId, Long sessionId);
    List<Ticket> findByScreenSessionId(Long sessionId);
    List<Ticket> findByRoomId(Long roomId);
    List<Ticket> findByScreenSessionIdAndRoomId(Long sessionId, Long roomId);
    List<Ticket> findByScreenSessionIdAndRoomIdAndIsAvailable(Long sessionId, Long roomId, boolean isAvailable);
    List<Ticket> findByScreenSessionIdAndOrderIsNotNull(Long sessionId);
}