package com.awbd.cinema.DTOs.SessionInfoDTOs;

import com.awbd.cinema.entities.SessionInfo;
import com.awbd.cinema.enums.Format;

public record SessionInfoDTO(
        Long id,
        Format format,
        Integer points
) {
    public static SessionInfoDTO from(SessionInfo sessionInfo) {
        return new SessionInfoDTO(
                sessionInfo.getId(),
                sessionInfo.getFormat(),
                sessionInfo.getPoints()
        );
    }
}
