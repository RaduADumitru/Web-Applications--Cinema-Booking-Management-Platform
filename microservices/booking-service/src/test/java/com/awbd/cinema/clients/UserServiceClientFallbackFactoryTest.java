package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.config.FeignClientErrorTranslator;
import com.awbd.cinema.exceptions.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceClientFallbackFactoryTest {

    private final UserServiceClientFallbackFactory factory =
            new UserServiceClientFallbackFactory(new FeignClientErrorTranslator(new ObjectMapper()));

    private FeignException notFound() {
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(404);
        lenient().when(fe.contentUTF8()).thenReturn("{\"message\":\"User not found.\"}");
        return fe;
    }

    @Test
    void getLoyaltyPoints_degradesToZero_onOutage() {
        LoyaltyPointsDTO result =
                factory.create(new RuntimeException("connection refused")).getLoyaltyPoints(7L);

        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.loyaltyPoints()).isZero();
    }

    @Test
    void getLoyaltyPoints_surfacesRealClientError() {
        assertThatThrownBy(() -> factory.create(notFound()).getLoyaltyPoints(7L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found.");
    }

    @Test
    void updateLoyaltyPoints_skipsUpdate_onOutage() {
        LoyaltyPointsDTO result = factory.create(new RuntimeException("timeout"))
                .updateLoyaltyPoints(7L, new AdjustLoyaltyPointsDTO(25));

        assertThat(result.loyaltyPoints()).isEqualTo(25);
    }

    @Test
    void updateLoyaltyPoints_surfacesRealClientError() {
        assertThatThrownBy(() -> factory.create(notFound())
                .updateLoyaltyPoints(7L, new AdjustLoyaltyPointsDTO(25)))
                .isInstanceOf(NotFoundException.class);
    }
}
