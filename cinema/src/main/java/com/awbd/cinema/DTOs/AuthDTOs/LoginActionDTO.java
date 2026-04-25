package com.awbd.cinema.DTOs.AuthDTOs;

import org.springframework.http.ResponseCookie;

import java.util.List;

public record LoginActionDTO(
        LoginResponseDTO response,
        LoginCookiesDTO cookies
) {
}

