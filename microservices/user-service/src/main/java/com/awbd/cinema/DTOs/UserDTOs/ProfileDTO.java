package com.awbd.cinema.DTOs.UserDTOs;

import com.awbd.cinema.entities.User;
import com.awbd.cinema.enums.Role;

public record ProfileDTO(
        Long id,
        String username,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        Integer loyaltyPoints,
        Role role
) {
    public static ProfileDTO from(User user){
        return new ProfileDTO(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getLoyaltyPoints(),
                user.getRole()
        );
    }
}
