package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import com.awbd.cinema.config.FeignClientErrorTranslator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    private final FeignClientErrorTranslator errorTranslator;

    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {
            @Override
            public LoyaltyPointsDTO getLoyaltyPoints(Long id) {
                // A real 4xx (e.g. user not found / bad request) is surfaced; only a genuine outage
                // degrades to 0 points so the booking flow can still proceed.
                RuntimeException clientError = errorTranslator.clientErrorOrNull(cause);
                if (clientError != null) {
                    throw clientError;
                }
                log.warn("user-service unavailable; treating loyalty points as 0 for user {}. Cause: {}",
                        id, cause.toString());
                return new LoyaltyPointsDTO(id, 0);
            }

            @Override
            public LoyaltyPointsDTO updateLoyaltyPoints(Long id, AdjustLoyaltyPointsDTO dto) {
                RuntimeException clientError = errorTranslator.clientErrorOrNull(cause);
                if (clientError != null) {
                    throw clientError;
                }
                log.warn("user-service unavailable; skipping loyalty-points update for user {}. Cause: {}",
                        id, cause.toString());
                return new LoyaltyPointsDTO(id, dto.loyaltyPoints());
            }
        };
    }
}
