package com.awbd.cinema.services.NotificationService;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.DTOs.NotificationDTOs.NotificationDTO;

import java.util.List;

public interface NotificationService {
    NotificationDTO createNotification(CreateNotificationDTO dto);
    List<NotificationDTO> getMyNotifications(Long userId);
    NotificationDTO getNotification(Long id);
    NotificationDTO markAsSent(Long id);
}
