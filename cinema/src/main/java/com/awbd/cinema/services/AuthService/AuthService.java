package com.awbd.cinema.services.AuthService;

import com.awbd.cinema.DTOs.AuthDTOs.*;

public interface AuthService {
    RegisterResponseDTO register(RegisterDTO register);

    LoginActionDTO login(LoginDTO login);
}
