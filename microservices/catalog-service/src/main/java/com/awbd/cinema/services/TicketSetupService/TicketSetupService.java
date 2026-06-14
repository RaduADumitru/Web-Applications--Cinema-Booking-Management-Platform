package com.awbd.cinema.services.TicketSetupService;

import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;

public interface TicketSetupService {
    TicketSetupDTO getTicketSetup(Long seatId, Long roomId, Long sessionId);
}
