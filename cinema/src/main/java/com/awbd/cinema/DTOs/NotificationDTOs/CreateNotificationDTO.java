package com.awbd.cinema.DTOs.NotificationDTOs;

import com.awbd.cinema.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateNotificationDTO(
        @NotNull(message = "Notification type is required.")
        NotificationType type,

        @NotBlank(message = "Notification content is required.")
        String content,

        @NotNull(message = "User ID is required.")
        Long userId
) {}
