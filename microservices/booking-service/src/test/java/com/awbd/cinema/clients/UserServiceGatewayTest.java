package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.config.FeignClientErrorTranslator;
import com.awbd.cinema.exceptions.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceGatewayTest {

    private UserServiceGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new UserServiceGateway(
                mock(UserServiceClient.class),
                new FeignClientErrorTranslator(new ObjectMapper()));
    }

    private FeignException feign(int status, String body) {
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(status);
        lenient().when(fe.contentUTF8()).thenReturn(body);
        return fe;
    }

    @Test
    void getLoyalty_propagatesNotFound_whenUserReturns404() {
        Throwable cause = feign(404, "{\"message\":\"User not found.\"}");

        assertThatThrownBy(() -> gateway.getLoyaltyPointsFallback(1L, cause))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found.");
    }

    @Test
    void getLoyalty_degradesToZeroPoints_onOutage() {
        Throwable cause = new RuntimeException("Connection refused"); // not a Feign 4xx

        LoyaltyPointsDTO result = gateway.getLoyaltyPointsFallback(1L, cause);

        assertThat(result.loyaltyPoints()).isZero();
    }

    @Test
    void updateLoyalty_propagatesNotFound_whenUserReturns404() {
        Throwable cause = feign(404, "{\"message\":\"User not found.\"}");

        assertThatThrownBy(() -> gateway.updateLoyaltyPointsFallback(1L, new AdjustLoyaltyPointsDTO(7), cause))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found.");
    }

    @Test
    void updateLoyalty_echoesRequestedValue_onOutage() {
        Throwable cause = new RuntimeException("Connection refused");

        LoyaltyPointsDTO result = gateway.updateLoyaltyPointsFallback(1L, new AdjustLoyaltyPointsDTO(7), cause);

        // Skip the update but echo the requested value so the caller can proceed.
        assertThat(result.loyaltyPoints()).isEqualTo(7);
    }
}
