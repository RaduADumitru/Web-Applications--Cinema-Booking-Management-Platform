package com.awbd.cinema.DTOs.NotificationDTOs;

import com.awbd.cinema.DTOs.OrderDTOs.OrderDTO;
import com.awbd.cinema.entities.Notification;
import com.awbd.cinema.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationDTO(
        Long id,
        NotificationType type,
        String content,
        LocalDateTime createdDate,
        LocalDateTime sentDate,
        Long userId,
        OrderDTO order
) {
    public static NotificationDTO from(Notification n) {
        OrderDTO orderDTO = n.getOrder() != null ? OrderDTO.from(n.getOrder()) : null;
        return new NotificationDTO(
                n.getId(),
                n.getType(),
                n.getContent(),
                n.getCreatedDate(),
                n.getSentDate(),
                n.getUserId(),
                orderDTO
        );
    }
}
