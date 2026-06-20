package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long>, JpaSpecificationExecutor<Seat> {
    List<Seat> findByRow(Integer row);

    @Query("SELECT s FROM Seat s JOIN s.rooms r WHERE r.id = :roomId AND s.id IN :seatIds")
    List<Seat> findByRoomIdAndSeatIds(@Param("roomId") Long roomId, @Param("seatIds") List<Long> seatIds);
}

