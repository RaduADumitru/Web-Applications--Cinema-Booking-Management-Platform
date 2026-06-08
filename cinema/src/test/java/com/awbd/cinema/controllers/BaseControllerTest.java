package com.awbd.cinema.controllers;

import com.awbd.cinema.security.CustomUserDetails;
import com.awbd.cinema.security.CustomUserDetailsService;
import com.awbd.cinema.security.JwtAuthenticationFilter;
import com.awbd.cinema.security.SecurityConfig;
import com.awbd.cinema.services.LoginAttemptService.LoginAttemptService;
import com.awbd.cinema.utils.JwtUtil;
import com.awbd.cinema.utils.SecurityCorsProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest
@Import(SecurityConfig.class)
public abstract class BaseControllerTest {
    @MockitoBean
    protected UserDetailsService userDetailsService;

    @MockitoBean
    protected LoginAttemptService loginAttemptService;

    @MockitoBean
    protected JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    protected SecurityCorsProperties securityCorsProperties;

    @MockitoBean
    protected JwtUtil jwtUtil;

}