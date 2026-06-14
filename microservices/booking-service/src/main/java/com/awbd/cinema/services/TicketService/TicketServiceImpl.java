package com.awbd.cinema.services.TicketService;

import com.awbd.cinema.DTOs.TicketDTOs.BookTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.SaveTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;
import com.awbd.cinema.clients.CatalogServiceClient;
import com.awbd.cinema.entities.Ticket;
import com.awbd.cinema.entities.TicketInfo;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.TicketInfoRepository;
import com.awbd.cinema.repositories.TicketRepository;
import com.awbd.cinema.utils.RestPage;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketInfoRepository ticketInfoRepository;
    private final CatalogServiceClient catalogServiceClient;

    @Override
    @Transactional
    @CacheEvict(value = "ticket_lists", allEntries = true)
    public TicketDTO createTicket(SaveTicketDTO dto) {
        if (ticketRepository.existsBySeatIdAndRoomIdAndScreenSessionId(dto.seatId(), dto.roomId(), dto.screenSessionId())) {
            throw new AlreadyExistsException("A ticket for this seat, room and session already exists.");
        }

        // Validate (seat-in-room, session-in-room) and fetch the pricing/display snapshot from catalog-service.
        TicketSetupDTO setup = catalogServiceClient.getTicketSetup(dto.seatId(), dto.roomId(), dto.screenSessionId());

        Ticket ticket = Ticket.builder()
                .isAvailable(true)
                .seatId(dto.seatId())
                .roomId(dto.roomId())
                .screenSessionId(dto.screenSessionId())
                .seatRow(setup.seatRow())
                .seatNumber(setup.seatNumber())
                .seatZone(setup.seatZone())
                .extraFee(setup.extraFee())
                .extraPoints(setup.extraPoints())
                .movieTitle(setup.movieTitle())
                .sessionDate(setup.sessionDate())
                .sessionStartTime(setup.sessionStartTime())
                .sessionPoints(setup.sessionPoints())
                .build();

        return TicketDTO.from(ticketRepository.save(ticket));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "ticket_lists")
    public RestPage<TicketDTO> getTickets(Long sessionId, Long roomId, Boolean isAvailable, Pageable pageable) {
        if (sessionId != null && roomId != null && isAvailable != null) {
            return new RestPage<>(ticketRepository.findByScreenSessionIdAndRoomIdAndIsAvailable(sessionId, roomId, isAvailable, pageable)
                    .map(TicketDTO::from));
        }
        if (sessionId != null && roomId != null) {
            return new RestPage<>(ticketRepository.findByScreenSessionIdAndRoomId(sessionId, roomId, pageable).map(TicketDTO::from));
        }
        if (sessionId != null) {
            return new RestPage<>(ticketRepository.findByScreenSessionId(sessionId, pageable).map(TicketDTO::from));
        }
        if (roomId != null) {
            return new RestPage<>(ticketRepository.findByRoomId(roomId, pageable).map(TicketDTO::from));
        }
        return new RestPage<>(ticketRepository.findAll(pageable).map(TicketDTO::from));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "single_ticket", key = "#id")
    public TicketDTO getTicket(Long id) {
        return ticketRepository.findById(id)
                .map(TicketDTO::from)
                .orElseThrow(() -> new NotFoundException("Ticket not found."));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_ticket", key = "#id"),
            @CacheEvict(value = "ticket_lists", allEntries = true)
    })
    public TicketDTO bookTicket(Long id, BookTicketDTO dto) {
        Ticket ticket = ticketRepository.findByIdForBooking(id)
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

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_ticket", key = "#id"),
            @CacheEvict(value = "ticket_lists", allEntries = true)
    })
    public void deleteTicket(Long id) {
        if (!ticketRepository.existsById(id)) {
            throw new NotFoundException("Ticket not found.");
        }
        ticketRepository.deleteById(id);
    }
}
