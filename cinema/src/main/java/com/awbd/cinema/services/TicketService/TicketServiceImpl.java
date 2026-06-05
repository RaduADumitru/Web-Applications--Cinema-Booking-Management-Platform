package com.awbd.cinema.services.TicketService;

import com.awbd.cinema.DTOs.TicketDTOs.BookTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.SaveTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketDTO;
import com.awbd.cinema.entities.Room;
import com.awbd.cinema.entities.ScreenSession;
import com.awbd.cinema.entities.Seat;
import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.entities.TicketInfo;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.RoomRepository;
import com.awbd.cinema.repositories.ScreenSessionRepository;
import com.awbd.cinema.repositories.SeatRepository;
import com.awbd.cinema.repositories.TicketInfoRepository;
import com.awbd.cinema.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final SeatRepository seatRepository;
    private final RoomRepository roomRepository;
    private final ScreenSessionRepository screenSessionRepository;
    private final TicketInfoRepository ticketInfoRepository;

    @Transactional
    public TicketDTO createTicket(SaveTicketDTO dto) {
        Seat seat = seatRepository.findById(dto.seatId())
                .orElseThrow(() -> new NotFoundException("Seat not found."));
        Room room = roomRepository.findById(dto.roomId())
                .orElseThrow(() -> new NotFoundException("Room not found."));
        ScreenSession session = screenSessionRepository.findById(dto.screenSessionId())
                .orElseThrow(() -> new NotFoundException("Screen session not found."));

        if (!roomRepository.existsByIdAndSeatsId(room.getId(), seat.getId())) {
            throw new BadRequestException("Seat does not belong to the specified room.");
        }
        if (!roomRepository.existsByIdAndScreenSessionsId(room.getId(), session.getId())) {
            throw new BadRequestException("Screen session is not scheduled in the specified room.");
        }
        if (ticketRepository.existsBySeatIdAndRoomIdAndScreenSessionId(seat.getId(), room.getId(), session.getId())) {
            throw new AlreadyExistsException("A ticket for this seat, room and session already exists.");
        }

        Ticket ticket = Ticket.builder()
                .isAvailable(true)
                .seat(seat)
                .room(room)
                .screenSession(session)
                .build();

        return toDTO(ticketRepository.save(ticket));
    }

    @Transactional(readOnly = true)
    public List<TicketDTO> getTickets(Long sessionId, Long roomId, Boolean isAvailable) {
        if (sessionId != null && roomId != null && isAvailable != null) {
            return ticketRepository.findByScreenSessionIdAndRoomIdAndIsAvailable(sessionId, roomId, isAvailable)
                    .stream().map(this::toDTO).toList();
        }
        if (sessionId != null && roomId != null) {
            return ticketRepository.findByScreenSessionIdAndRoomId(sessionId, roomId)
                    .stream().map(this::toDTO).toList();
        }
        if (sessionId != null) {
            return ticketRepository.findByScreenSessionId(sessionId)
                    .stream().map(this::toDTO).toList();
        }
        if (roomId != null) {
            return ticketRepository.findByRoomId(roomId)
                    .stream().map(this::toDTO).toList();
        }
        return ticketRepository.findAll().stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public TicketDTO getTicket(Long id) {
        return ticketRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new NotFoundException("Ticket not found."));
    }

    @Transactional
    public TicketDTO bookTicket(Long id, BookTicketDTO dto) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found."));
        if (!ticket.isAvailable()) {
            throw new BadRequestException("Ticket is already booked.");
        }

        TicketInfo info = ticketInfoRepository.findByType(dto.type())
                .orElseThrow(() -> new NotFoundException(
                    "No price configured for type '" + dto.type() + "'."));

        ticket.setType(dto.type());
        ticket.setTicketInfo(info);
        ticket.setAvailable(false);
        return TicketDTO.from(ticketRepository.save(ticket));
    }

    @Transactional
    public void deleteTicket(Long id) {
        if (!ticketRepository.existsById(id)) {
            throw new NotFoundException("Ticket not found.");
        }
        ticketRepository.deleteById(id);
    }

    private TicketDTO toDTO(Ticket ticket) {
        return TicketDTO.from(ticket);
    }
}
