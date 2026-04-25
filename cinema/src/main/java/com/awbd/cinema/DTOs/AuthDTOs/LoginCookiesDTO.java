package com.awbd.cinema.DTOs.AuthDTOs;

import org.springframework.http.ResponseCookie;

public record LoginCookiesDTO(
        ResponseCookie jwtCookie,
        ResponseCookie refreshTokenCookie
) {
}
