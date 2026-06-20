package com.awbd.cinema.security;

import com.awbd.cinema.utils.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceTokenAuthenticationFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private ServiceTokenAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_ShouldAuthenticateAsService_WhenBearerTokenIsValid()
            throws ServletException, IOException {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer service_token");
        when(jwtUtil.validateServiceToken("service_token")).thenReturn("booking-service");

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("booking-service");
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_SERVICE"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ShouldNotAuthenticate_WhenNoAuthorizationHeader()
            throws ServletException, IOException {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ShouldNotAuthenticate_WhenTokenIsInvalid()
            throws ServletException, IOException {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer bad_token");
        when(jwtUtil.validateServiceToken("bad_token")).thenThrow(new JwtException("bad"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
