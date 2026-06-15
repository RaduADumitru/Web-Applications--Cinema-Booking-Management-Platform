package com.awbd.cinema.services.SessionInfoService;

import com.awbd.cinema.DTOs.SessionInfoDTOs.SaveSessionInfoDTO;
import com.awbd.cinema.DTOs.SessionInfoDTOs.SessionInfoDTO;
import com.awbd.cinema.entities.SessionInfo;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.SessionInfoRepository;
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
public class SessionInfoServiceImpl implements SessionInfoService {

    private final SessionInfoRepository sessionInfoRepository;

    @Override
    @Transactional
    @CacheEvict(value = "session_info_lists", allEntries = true)
    public SessionInfoDTO createSessionInfo(SaveSessionInfoDTO dto) {
        SessionInfo sessionInfo = SessionInfo.builder()
                .format(dto.format())
                .points(dto.points())
                .build();
        return SessionInfoDTO.from(sessionInfoRepository.save(sessionInfo));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "session_info_lists")
    public RestPage<SessionInfoDTO> getSessionInfos(Pageable pageable) {
        return new RestPage<>(sessionInfoRepository.findAll(pageable).map(SessionInfoDTO::from));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "single_session_infos", key = "#id")
    public SessionInfoDTO getSessionInfo(Long id) {
        return sessionInfoRepository.findById(id)
                .map(SessionInfoDTO::from)
                .orElseThrow(() -> new NotFoundException("Session info not found."));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_session_infos", key = "#id"),
            @CacheEvict(value = "session_info_lists", allEntries = true),
            @CacheEvict(value = "single_screen_sessions", allEntries = true),
            @CacheEvict(value = "screen_session_lists", allEntries = true),
            @CacheEvict(value = "movie_session_lists", allEntries = true)
    })
    public SessionInfoDTO updateSessionInfo(Long id, SaveSessionInfoDTO dto) {
        SessionInfo sessionInfo = sessionInfoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Session info not found."));
        sessionInfo.setFormat(dto.format());
        sessionInfo.setPoints(dto.points());
        return SessionInfoDTO.from(sessionInfoRepository.save(sessionInfo));
    }
}
