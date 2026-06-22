package com.awbd.cinema.services.AuthService;

import com.awbd.cinema.DTOs.AuthDTOs.LoginActionDTO;
import com.awbd.cinema.DTOs.AuthDTOs.LoginCookiesDTO;
import com.awbd.cinema.DTOs.AuthDTOs.LoginDTO;
import com.awbd.cinema.DTOs.AuthDTOs.RegisterDTO;
import com.awbd.cinema.DTOs.AuthDTOs.RegisterResponseDTO;

public interface AuthService {
    RegisterResponseDTO register(RegisterDTO register);

    LoginActionDTO login(LoginDTO login);

    void createOwner(RegisterDTO owner);

    LoginCookiesDTO refreshTokens(String refreshToken);

    LoginCookiesDTO logoutCookies();
}
