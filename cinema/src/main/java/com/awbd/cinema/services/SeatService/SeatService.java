package com.awbd.cinema.services.SeatService;

import com.awbd.cinema.DTOs.SeatDTOs.SeatDTO;
import com.awbd.cinema.DTOs.SeatDTOs.SaveSeatDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SeatService {
    SeatDTO createSeat(SaveSeatDTO dto);
    Page<SeatDTO> getSeats(String roomType, Long screenSessionId, Long movieId, Pageable pageable);
    SeatDTO getSeat(Long id);
    SeatDTO updateSeat(Long id, SaveSeatDTO dto);
    void deleteSeat(Long id);
}
