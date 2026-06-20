package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.config.FeignClientErrorTranslator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingServiceClientFallbackFactory implements FallbackFactory<BookingServiceClient> {

    private final FeignClientErrorTranslator errorTranslator;

    @Override
    public BookingServiceClient create(Throwable cause) {
        return new BookingServiceClient() {
            @Override
            public void createNotification(CreateNotificationDTO dto) {
                // A real 4xx (e.g. a malformed notification request) is surfaced so the bug is not
                // swallowed; only a genuine booking-service outage is skipped silently.
                RuntimeException clientError = errorTranslator.clientErrorOrNull(cause);
                if (clientError != null) {
                    throw clientError;
                }
                log.warn("booking-service unavailable; skipping notification of type {} for user {}. Cause: {}",
                        dto.type(), dto.userId(), cause.toString());
            }
        };
    }
}
