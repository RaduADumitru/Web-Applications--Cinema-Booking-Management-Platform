package com.awbd.cinema.services.ScreenSessionService;

import com.awbd.cinema.DTOs.ScreenSessionDTOs.SaveScreenSessionDTO;
import com.awbd.cinema.DTOs.ScreenSessionDTOs.ScreenSessionDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ScreenSessionService {
    ScreenSessionDTO createScreenSession(SaveScreenSessionDTO dto);
    Page<ScreenSessionDTO> getScreenSessions(Long movieId, String format, Pageable pageable);
    Page<ScreenSessionDTO> getScreenSessionsByMovie(Long movieId, Pageable pageable);
    ScreenSessionDTO getScreenSession(Long id);
    ScreenSessionDTO updateScreenSession(Long id, SaveScreenSessionDTO dto);
    void deleteScreenSession(Long id);
}
