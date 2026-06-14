package com.awbd.cinema.services.TicketSetupService;

import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.entities.Room;
import com.awbd.cinema.entities.ScreenSession;
import com.awbd.cinema.entities.Seat;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.RoomRepository;
import com.awbd.cinema.repositories.ScreenSessionRepository;
import com.awbd.cinema.repositories.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TicketSetupServiceImpl implements TicketSetupService {

    private final SeatRepository seatRepository;
    private final RoomRepository roomRepository;
    private final ScreenSessionRepository screenSessionRepository;

    @Override
    @Transactional(readOnly = true)
    public TicketSetupDTO getTicketSetup(Long seatId, Long roomId, Long sessionId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new NotFoundException("Seat not found."));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found."));
        ScreenSession session = screenSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Screen session not found."));

        if (!roomRepository.existsByIdAndSeatsId(room.getId(), seat.getId())) {
            throw new BadRequestException("Seat does not belong to the specified room.");
        }
        if (!roomRepository.existsByIdAndScreenSessionsId(room.getId(), session.getId())) {
            throw new BadRequestException("Screen session is not scheduled in the specified room.");
        }

        BigDecimal extraFee = seat.getCategory() != null ? seat.getCategory().getExtraFee() : BigDecimal.ZERO;
        Integer extraPoints = seat.getCategory() != null ? seat.getCategory().getExtraPoints() : 0;
        Integer sessionPoints = session.getSessionInfo() != null ? session.getSessionInfo().getPoints() : 0;

        return new TicketSetupDTO(
                seat.getRow(),
                seat.getNumber(),
                seat.getZone().name(),
                extraFee,
                extraPoints,
                session.getMovie().getTitle(),
                session.getDate(),
                session.getStartTime(),
                sessionPoints
        );
    }
}
