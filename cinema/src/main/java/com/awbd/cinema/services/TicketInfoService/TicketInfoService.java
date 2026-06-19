package com.awbd.cinema.services.TicketInfoService;

import com.awbd.cinema.DTOs.TicketInfoDTOs.SaveTicketInfoDTO;
import com.awbd.cinema.DTOs.TicketInfoDTOs.TicketInfoDTO;
import com.awbd.cinema.utils.RestPage;

import java.util.List;

public interface TicketInfoService {
    TicketInfoDTO createTicketInfo(SaveTicketInfoDTO dto);
    RestPage<TicketInfoDTO> getTicketInfos();
    TicketInfoDTO getTicketInfo(Long id);
    TicketInfoDTO updateTicketInfo(Long id, SaveTicketInfoDTO dto);
    void deleteTicketInfo(Long id);
}
