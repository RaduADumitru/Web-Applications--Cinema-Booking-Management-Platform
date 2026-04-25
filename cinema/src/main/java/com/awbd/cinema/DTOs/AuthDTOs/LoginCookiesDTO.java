package com.awbd.cinema.DTOs.AuthDTOs;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseCookie;

public record LoginCookiesDTO(
        ResponseCookie jwtCookie,
        ResponseCookie refreshTokenCookie
) {
}
