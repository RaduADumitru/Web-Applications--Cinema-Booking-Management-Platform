package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.config.FeignClientErrorTranslator;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.exceptions.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingServiceGatewayTest {

    private BookingServiceGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new BookingServiceGateway(
                mock(BookingServiceClient.class),
                new FeignClientErrorTranslator(new ObjectMapper()));
    }

    private CreateNotificationDTO sampleDto() {
        return new CreateNotificationDTO(NotificationType.EMAIL_VERIFICATION, "hello", 42L);
    }

    private FeignException feign(int status, String body) {
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(status);
        lenient().when(fe.contentUTF8()).thenReturn(body);
        return fe;
    }

    @Test
    void propagatesClientError_whenBookingReturns400() {
        Throwable cause = feign(400, "{\"message\":\"Malformed notification.\"}");

        assertThatThrownBy(() -> gateway.createNotificationFallback(sampleDto(), cause))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Malformed notification.");
    }

    @Test
    void skipsSilently_whenBookingIsUnavailable() {
        Throwable cause = new RuntimeException("Connection refused"); // not a Feign 4xx

        // Best-effort: an outage must NOT break registration — the fallback returns normally.
        assertThatCode(() -> gateway.createNotificationFallback(sampleDto(), cause))
                .doesNotThrowAnyException();
    }
}
