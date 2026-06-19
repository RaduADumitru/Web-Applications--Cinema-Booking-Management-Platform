package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "booking-service",
        path = "/api/v1",
        fallbackFactory = BookingServiceClientFallbackFactory.class
)
public interface BookingServiceClient {

    @PostMapping("/internal/notifications")
    void createNotification(@RequestBody CreateNotificationDTO dto);
}
