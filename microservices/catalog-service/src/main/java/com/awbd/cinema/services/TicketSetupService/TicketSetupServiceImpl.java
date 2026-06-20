package com.awbd.cinema.services.TicketSetupService;

import com.awbd.cinema.DTOs.TicketDTOs.BulkSaveTicketsDTO;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


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
        // findActiveById (not findById) so a session whose movie is soft-deleted is treated as
        // not found (404) rather than 500ing when session.getMovie().getTitle() resolves the
        // @SQLRestriction-filtered proxy below.
        ScreenSession session = screenSessionRepository.findActiveById(sessionId)
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

    @Override
    @Transactional(readOnly = true)
    public List<TicketSetupDTO> getTicketSetups(BulkSaveTicketsDTO dto) {
        Room room = roomRepository.findById(dto.roomId())
                .orElseThrow(() -> new NotFoundException("Room not found."));
        ScreenSession session = screenSessionRepository.findActiveById(dto.screenSessionId())
                .orElseThrow(() -> new NotFoundException("Screen session not found."));

        if (!roomRepository.existsByIdAndScreenSessionsId(room.getId(), session.getId())) {
            throw new BadRequestException("Screen session is not scheduled in the specified room.");
        }

        List<Seat> seats = seatRepository.findAllById(dto.seatIds());
        if (seats.size() < dto.seatIds().size()) {
            throw new NotFoundException("Seat not found.");
        }

        List<Seat> roomSeats = seatRepository.findByRoomIdAndSeatIds(room.getId(), dto.seatIds());
        if (roomSeats.size() < seats.size()) {
            throw new BadRequestException("Seat does not belong to the specified room.");
        }

        Map<Long, Seat> seatMap = seats.stream()
                .collect(Collectors.toMap(Seat::getId, Function.identity()));

        List<TicketSetupDTO> setups = new ArrayList<>();
        for (Long seatId : dto.seatIds()) {
            Seat seat = seatMap.get(seatId);
            BigDecimal extraFee = seat.getCategory() != null ? seat.getCategory().getExtraFee() : BigDecimal.ZERO;
            Integer extraPoints = seat.getCategory() != null ? seat.getCategory().getExtraPoints() : 0;
            Integer sessionPoints = session.getSessionInfo() != null ? session.getSessionInfo().getPoints() : 0;

            setups.add(new TicketSetupDTO(
                    seat.getRow(),
                    seat.getNumber(),
                    seat.getZone().name(),
                    extraFee,
                    extraPoints,
                    session.getMovie().getTitle(),
                    session.getDate(),
                    session.getStartTime(),
                    sessionPoints
            ));
        }
        return setups;
    }
}
