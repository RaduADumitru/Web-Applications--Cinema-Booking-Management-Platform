package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationServiceClientFallback implements NotificationServiceClient {

    @Override
    public void createNotification(CreateNotificationDTO dto) {
        log.warn("booking-service unavailable; skipping notification of type {} for user {}.",
                dto.type(), dto.userId());
    }
}
