package com.awbd.cinema.services.NotificationService;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.DTOs.NotificationDTOs.NotificationDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    NotificationDTO createNotification(CreateNotificationDTO dto);
    Page<NotificationDTO> getMyNotifications(Long userId, Pageable pageable);
    NotificationDTO getNotification(Long id);
    NotificationDTO markAsSent(Long id);
}
