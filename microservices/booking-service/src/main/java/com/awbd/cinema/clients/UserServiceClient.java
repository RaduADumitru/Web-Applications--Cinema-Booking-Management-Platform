package com.awbd.cinema.clients;

import com.awbd.cinema.DTOs.UserDTOs.AdjustLoyaltyPointsDTO;
import com.awbd.cinema.DTOs.UserDTOs.LoyaltyPointsDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "user-service",
        path = "/api/v1",
        fallbackFactory = UserServiceClientFallbackFactory.class
)
public interface UserServiceClient {

    @GetMapping("/internal/users/{id}/loyalty-points")
    LoyaltyPointsDTO getLoyaltyPoints(@PathVariable Long id);

    @PatchMapping("/internal/users/{id}/loyalty-points")
    LoyaltyPointsDTO updateLoyaltyPoints(@PathVariable Long id, @RequestBody AdjustLoyaltyPointsDTO dto);
}
