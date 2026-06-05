package com.awbd.cinema.services.RoomService;

import com.awbd.cinema.DTOs.RoomDTOs.RoomDTO;
import com.awbd.cinema.DTOs.RoomDTOs.SaveRoomDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RoomService {
    RoomDTO createRoom(SaveRoomDTO dto);
    Page<RoomDTO> getRooms(Pageable pageable);
    RoomDTO getRoom(Long id);
    RoomDTO updateRoom(Long id, SaveRoomDTO dto);
    void deleteRoom(Long id);
    RoomDTO addSeat(Long roomId, Long seatId);
    RoomDTO addScreenSession(Long roomId, Long sessionId);
    RoomDTO removeSeat(Long roomId, Long seatId);
    RoomDTO removeScreenSession(Long roomId, Long sessionId);
}
