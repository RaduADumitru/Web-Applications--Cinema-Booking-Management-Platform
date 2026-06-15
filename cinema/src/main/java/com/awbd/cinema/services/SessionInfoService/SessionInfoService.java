package com.awbd.cinema.services.SessionInfoService;

import com.awbd.cinema.DTOs.SessionInfoDTOs.SaveSessionInfoDTO;
import com.awbd.cinema.DTOs.SessionInfoDTOs.SessionInfoDTO;
import com.awbd.cinema.utils.RestPage;
import org.springframework.data.domain.Pageable;

public interface SessionInfoService {
    SessionInfoDTO createSessionInfo(SaveSessionInfoDTO dto);
    RestPage<SessionInfoDTO> getSessionInfos(Pageable pageable);
    SessionInfoDTO getSessionInfo(Long id);
    SessionInfoDTO updateSessionInfo(Long id, SaveSessionInfoDTO dto);
}
