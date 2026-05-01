package com.awbd.cinema.DTOs.AuthDTOs;

public record LoginActionDTO(
        LoginResponseDTO response,
        LoginCookiesDTO cookies
) {
}

