package com.awbd.cinema.services.SeatService;

import com.awbd.cinema.DTOs.SeatDTOs.SeatDTO;
import com.awbd.cinema.DTOs.SeatDTOs.SaveSeatDTO;

import java.util.List;

public interface SeatService {
    SeatDTO createSeat(SaveSeatDTO dto);
    List<SeatDTO> getSeats(String roomType, Long screenSessionId, Long movieId);
    SeatDTO getSeat(Long id);
    SeatDTO updateSeat(Long id, SaveSeatDTO dto);
    void deleteSeat(Long id);
}
