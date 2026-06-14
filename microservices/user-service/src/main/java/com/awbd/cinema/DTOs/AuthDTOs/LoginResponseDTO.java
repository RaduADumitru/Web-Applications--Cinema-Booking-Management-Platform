package com.awbd.cinema.DTOs.AuthDTOs;

import com.awbd.cinema.entities.User;
import com.awbd.cinema.enums.Role;
import lombok.Builder;

@Builder
public record LoginResponseDTO(
        String username,
        String email,
        String firstName,
        String lastName,
        Role role
) {
    public static LoginResponseDTO from(User user){
        return LoginResponseDTO.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .build();
    }
}
