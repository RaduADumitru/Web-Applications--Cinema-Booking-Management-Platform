package com.awbd.cinema.DTOs.UserDTOs;

import com.awbd.cinema.entities.User;

public record ProfileDTO(
        String username,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        Integer loyaltyPoints,
        String role
) {
    public static ProfileDTO from(User user){
        return new ProfileDTO(
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getLoyaltyPoints(),
                user.getRole().name()
        );
    }
}
