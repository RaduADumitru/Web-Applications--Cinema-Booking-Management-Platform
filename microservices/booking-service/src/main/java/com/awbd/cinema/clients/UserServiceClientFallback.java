package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public LoyaltyPointsDTO getLoyaltyPoints(Long id) {
        log.warn("user-service unavailable; treating loyalty points as 0 for user {}.", id);
        return new LoyaltyPointsDTO(id, 0);
    }

    @Override
    public LoyaltyPointsDTO updateLoyaltyPoints(Long id, AdjustLoyaltyPointsDTO dto) {
        log.warn("user-service unavailable; skipping loyalty-points update for user {}.", id);
        return new LoyaltyPointsDTO(id, dto.loyaltyPoints());
    }
}
