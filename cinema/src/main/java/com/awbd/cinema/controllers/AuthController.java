package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.AuthDTOs.*;
import com.awbd.cinema.services.AuthService.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieCsrfTokenRepository csrfTokenRepository;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(@Valid @RequestBody RegisterDTO register) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(register));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginDTO login,HttpServletRequest request, HttpServletResponse response) {
        LoginActionDTO loginAction = authService.login(login);
        LoginCookiesDTO cookies = loginAction.cookies();

        CsrfToken csrfToken = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(csrfToken, request, response);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.jwtCookie().toString())
            .header(HttpHeaders.SET_COOKIE, cookies.refreshTokenCookie().toString())
            .body(loginAction.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@CookieValue(name = "refresh") String refreshToken) {
        LoginCookiesDTO cookies = authService.refreshTokens(refreshToken);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.jwtCookie().toString())
            .header(HttpHeaders.SET_COOKIE, cookies.refreshTokenCookie().toString())
            .body(Map.of("message", "Token refreshed successfully."));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        LoginCookiesDTO cookies = authService.logoutCookies();

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.jwtCookie().toString())
            .header(HttpHeaders.SET_COOKIE, cookies.refreshTokenCookie().toString())
            .body(Map.of("message", "Logged out successfully."));
    }
}

