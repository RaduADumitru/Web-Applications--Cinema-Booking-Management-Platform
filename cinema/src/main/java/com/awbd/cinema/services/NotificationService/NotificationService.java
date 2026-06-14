package com.awbd.cinema.services.NotificationService;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.DTOs.NotificationDTOs.NotificationDTO;
import com.awbd.cinema.utils.RestPage;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    NotificationDTO createNotification(CreateNotificationDTO dto);
    RestPage<NotificationDTO> getMyNotifications(Long userId, Pageable pageable);
    NotificationDTO getNotification(Long id);
    NotificationDTO markAsSent(Long id);
}
