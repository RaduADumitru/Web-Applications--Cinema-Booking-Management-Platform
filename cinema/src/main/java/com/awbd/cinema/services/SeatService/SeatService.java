package com.awbd.cinema.services.SeatService;

import com.awbd.cinema.DTOs.SeatDTOs.GenerateSeatsDTO;
import com.awbd.cinema.DTOs.SeatDTOs.SaveSeatDTO;
import com.awbd.cinema.DTOs.SeatDTOs.SeatDTO;
import com.awbd.cinema.utils.RestPage;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SeatService {
    SeatDTO createSeat(SaveSeatDTO dto);
    List<SeatDTO> generateSeats(GenerateSeatsDTO dto);
    RestPage<SeatDTO> getSeats(String roomType, Long screenSessionId, Long movieId, Pageable pageable);
    SeatDTO getSeat(Long id);
    SeatDTO updateSeat(Long id, SaveSeatDTO dto);
    void deleteSeat(Long id);
}
