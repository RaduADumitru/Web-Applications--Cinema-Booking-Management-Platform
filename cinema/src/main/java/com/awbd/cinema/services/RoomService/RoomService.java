package com.awbd.cinema.services.RoomService;

import com.awbd.cinema.DTOs.RoomDTOs.RoomDTO;
import com.awbd.cinema.DTOs.RoomDTOs.SaveRoomDTO;

import java.util.List;

public interface RoomService {
    RoomDTO createRoom(SaveRoomDTO dto);
    List<RoomDTO> getRooms();
    RoomDTO getRoom(Long id);
    RoomDTO updateRoom(Long id, SaveRoomDTO dto);
    void deleteRoom(Long id);
    RoomDTO addSeat(Long roomId, Long seatId);
    RoomDTO addScreenSession(Long roomId, Long sessionId);
    RoomDTO removeSeat(Long roomId, Long seatId);
    RoomDTO removeScreenSession(Long roomId, Long sessionId);
}
