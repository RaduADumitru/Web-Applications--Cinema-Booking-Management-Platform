package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {
            @Override
            public LoyaltyPointsDTO getLoyaltyPoints(Long id) {
                log.warn("user-service unavailable; treating loyalty points as 0 for user {}. Cause: {}",
                        id, cause.toString());
                return new LoyaltyPointsDTO(id, 0);
            }

            @Override
            public LoyaltyPointsDTO updateLoyaltyPoints(Long id, AdjustLoyaltyPointsDTO dto) {
                log.warn("user-service unavailable; skipping loyalty-points update for user {}. Cause: {}",
                        id, cause.toString());
                return new LoyaltyPointsDTO(id, dto.loyaltyPoints());
            }
        };
    }
}
