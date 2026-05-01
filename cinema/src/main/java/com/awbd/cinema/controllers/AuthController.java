package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.AuthDTOs.*;
import com.awbd.cinema.services.AuthService.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(@Valid @RequestBody RegisterDTO register) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(register));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginDTO login) {
        LoginActionDTO loginAction = authService.login(login);
        LoginCookiesDTO cookies = loginAction.cookies();

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.jwtCookie().toString())
            .header(HttpHeaders.SET_COOKIE, cookies.refreshTokenCookie().toString())
            .body(loginAction.response());
    }
}
