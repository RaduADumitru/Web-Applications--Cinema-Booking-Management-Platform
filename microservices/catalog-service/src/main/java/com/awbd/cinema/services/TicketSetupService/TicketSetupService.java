package com.awbd.cinema.services.TicketSetupService;

import com.awbd.cinema.DTOs.TicketDTOs.BulkSaveTicketsDTO;
import com.awbd.cinema.DTOs.TicketDTOs.TicketSetupDTO;

import java.util.List;

public interface TicketSetupService {
    TicketSetupDTO getTicketSetup(Long seatId, Long roomId, Long sessionId);
    List<TicketSetupDTO> getTicketSetups(BulkSaveTicketsDTO dto);
}

