package com.awbd.cinema.services.TicketInfoService;

import com.awbd.cinema.DTOs.TicketInfoDTOs.SaveTicketInfoDTO;
import com.awbd.cinema.DTOs.TicketInfoDTOs.TicketInfoDTO;

import java.util.List;

public interface TicketInfoService {
    TicketInfoDTO createTicketInfo(SaveTicketInfoDTO dto);
    List<TicketInfoDTO> getTicketInfos();
    TicketInfoDTO getTicketInfo(Long id);
    TicketInfoDTO updateTicketInfo(Long id, SaveTicketInfoDTO dto);
    void deleteTicketInfo(Long id);
}
