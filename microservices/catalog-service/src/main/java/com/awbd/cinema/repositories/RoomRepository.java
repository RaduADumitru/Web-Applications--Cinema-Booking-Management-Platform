package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.Room;
import com.awbd.cinema.enums.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByType(RoomType type);
    List<Room> findByFloor(Integer floor);
    boolean existsByIdAndSeatsId(Long roomId, Long seatId);
    boolean existsByIdAndScreenSessionsId(Long roomId, Long sessionId);
}
