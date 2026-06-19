package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BookingServiceClientFallbackFactory implements FallbackFactory<BookingServiceClient> {

    @Override
    public BookingServiceClient create(Throwable cause) {
        return new BookingServiceClient() {
            @Override
            public void createNotification(CreateNotificationDTO dto) {
                log.warn("booking-service unavailable; skipping notification of type {} for user {}. Cause: {}",
                        dto.type(), dto.userId(), cause.toString());
            }
        };
    }
}
