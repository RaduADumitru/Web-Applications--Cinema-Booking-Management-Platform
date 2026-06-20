package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.config.FeignClientErrorTranslator;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.exceptions.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingServiceClientFallbackFactoryTest {

    private final BookingServiceClientFallbackFactory factory =
            new BookingServiceClientFallbackFactory(new FeignClientErrorTranslator(new ObjectMapper()));

    private final CreateNotificationDTO dto =
            new CreateNotificationDTO(NotificationType.values()[0], "hello", 1L);

    @Test
    void createNotification_isSkippedSilently_onOutage() {
        assertThatNoException().isThrownBy(
                () -> factory.create(new RuntimeException("connection refused")).createNotification(dto));
    }

    @Test
    void createNotification_surfacesRealClientError() {
        FeignException badRequest = mock(FeignException.class);
        when(badRequest.status()).thenReturn(400);
        lenient().when(badRequest.contentUTF8()).thenReturn("{\"message\":\"Invalid notification.\"}");

        assertThatThrownBy(() -> factory.create(badRequest).createNotification(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid notification.");
    }
}
