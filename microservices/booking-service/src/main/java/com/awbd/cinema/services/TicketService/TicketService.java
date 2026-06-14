package com.awbd.cinema.services.TicketService;

import com.awbd.cinema.DTOs.TicketDTOs.BookTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.SaveTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketDTO;
import com.awbd.cinema.utils.RestPage;
import org.springframework.data.domain.Pageable;

public interface TicketService {
    TicketDTO createTicket(SaveTicketDTO dto);
    RestPage<TicketDTO> getTickets(Long sessionId, Long roomId, Boolean isAvailable, Pageable pageable);
    TicketDTO getTicket(Long id);
    TicketDTO bookTicket(Long id, BookTicketDTO dto);
    void deleteTicket(Long id);
}
