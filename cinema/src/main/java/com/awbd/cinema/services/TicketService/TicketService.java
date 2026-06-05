package com.awbd.cinema.services.TicketService;

import com.awbd.cinema.DTOs.TicketDTOs.BookTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.SaveTicketDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketDTO;

import java.util.List;

public interface TicketService {
    TicketDTO createTicket(SaveTicketDTO dto);
    List<TicketDTO> getTickets(Long sessionId, Long roomId, Boolean isAvailable);
    TicketDTO getTicket(Long id);
    TicketDTO bookTicket(Long id, BookTicketDTO dto);
    void deleteTicket(Long id);
}
