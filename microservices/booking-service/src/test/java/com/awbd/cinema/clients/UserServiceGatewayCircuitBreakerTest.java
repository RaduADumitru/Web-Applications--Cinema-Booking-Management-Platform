package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "resilience4j.circuitbreaker.instances.user-service.sliding-window-type=COUNT_BASED",
        "resilience4j.circuitbreaker.instances.user-service.sliding-window-size=2",
        "resilience4j.circuitbreaker.instances.user-service.minimum-number-of-calls=2",
        "resilience4j.circuitbreaker.instances.user-service.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.user-service.wait-duration-in-open-state=60s",
        "resilience4j.circuitbreaker.instances.user-service.permitted-number-of-calls-in-half-open-state=1",
        "resilience4j.retry.instances.user-service.max-attempts=1"
})
@ActiveProfiles("test")
public class UserServiceGatewayCircuitBreakerTest {

    @MockitoBean
    private UserServiceClient userServiceClient;

    @Autowired
    private UserServiceGateway userServiceGateway;

    @Test
    @DisplayName("Open circuit degrades to 0 points instead of throwing CallNotPermittedException")
    void openCircuit_degradesGracefully_insteadOfThrowing() {
        when(userServiceClient.getLoyaltyPoints(anyLong()))
                .thenThrow(new RuntimeException("simulated outage"));

        List<LoyaltyPointsDTO> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            // None of these should throw — the fallback must absorb CallNotPermittedException too
            LoyaltyPointsDTO result = userServiceGateway.getLoyaltyPoints(1L);
            results.add(result);
        }

        assertThat(results).hasSize(5);
        assertThat(results).allSatisfy(dto ->
                assertThat(dto.loyaltyPoints()).isEqualTo(0)
        );

        // Circuit must have opened: calls 3–5 were short-circuited, so the real client
        // was invoked at most twice (the two calls needed to trip the breaker).
        verify(userServiceClient, atMost(2)).getLoyaltyPoints(anyLong());
    }
}
