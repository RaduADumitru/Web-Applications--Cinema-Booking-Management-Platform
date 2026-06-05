package com.awbd.cinema.services.ScreenSessionService;

import com.awbd.cinema.DTOs.ScreenSessionDTOs.SaveScreenSessionDTO;
import com.awbd.cinema.DTOs.ScreenSessionDTOs.ScreenSessionDTO;

import java.util.List;

public interface ScreenSessionService {
    ScreenSessionDTO createScreenSession(SaveScreenSessionDTO dto);
    List<ScreenSessionDTO> getScreenSessions(Long movieId, String format);
    List<ScreenSessionDTO> getScreenSessionsByMovie(Long movieId);
    ScreenSessionDTO getScreenSession(Long id);
    ScreenSessionDTO updateScreenSession(Long id, SaveScreenSessionDTO dto);
    void deleteScreenSession(Long id);
}
