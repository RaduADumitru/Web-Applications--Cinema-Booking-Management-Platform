package com.awbd.cinema.security;

import com.awbd.cinema.enums.Role;
import com.awbd.cinema.utils.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "cookieSecure", true);
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "cookieSameSite", "Lax");
    }

    @Test
    void doFilterInternal_ShouldPassThrough_WhenNoCookiesExist() throws ServletException, IOException {
        when(request.getCookies()).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_ShouldPassThrough_WhenJwtCookieNotPresent() throws ServletException, IOException {
        Cookie otherCookie = new Cookie("theme", "dark");
        when(request.getCookies()).thenReturn(new Cookie[]{otherCookie});

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_ShouldPassThroughEarly_WhenTokenIsNotAccessToken() throws ServletException, IOException {
        Cookie jwtCookie = new Cookie("jwt", "refresh_token_value");
        when(request.getCookies()).thenReturn(new Cookie[]{jwtCookie});
        when(jwtUtil.extractClaim(eq("refresh_token_value"), any())).thenReturn("REFRESH");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtUtil, never()).extractUsername(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_ShouldAuthenticate_WhenTokenIsValidAndNoPriorAuthentication() throws ServletException, IOException {
        Cookie jwtCookie = new Cookie("jwt", "valid_access_token");

        when(request.getCookies()).thenReturn(new Cookie[]{jwtCookie});
        when(jwtUtil.extractClaim(eq("valid_access_token"), any())).thenReturn("ACCESS");
        when(jwtUtil.extractUsername("valid_access_token")).thenReturn("cinema_fan");
        when(jwtUtil.isTokenValid("valid_access_token", "cinema_fan")).thenReturn(true);
        when(jwtUtil.extractUserId("valid_access_token")).thenReturn(7L);
        when(jwtUtil.extractRole("valid_access_token")).thenReturn(Role.USER);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("cinema_fan");
        assertThat(auth.getPrincipal()).isInstanceOf(CustomUserDetails.class);
        assertThat(((CustomUserDetails) auth.getPrincipal()).getId()).isEqualTo(7L);
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    void doFilterInternal_ShouldNotReauthenticate_WhenAuthenticationAlreadyExists() throws ServletException, IOException {
        Cookie jwtCookie = new Cookie("jwt", "valid_access_token");
        Authentication existingAuth = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        when(request.getCookies()).thenReturn(new Cookie[]{jwtCookie});
        when(jwtUtil.extractClaim(eq("valid_access_token"), any())).thenReturn("ACCESS");
        when(jwtUtil.extractUsername("valid_access_token")).thenReturn("cinema_fan");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtUtil, never()).extractUserId(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(existingAuth);
    }

    @Test
    void doFilterInternal_ShouldClearCookie_WhenJwtExceptionIsThrown() throws ServletException, IOException {
        Cookie jwtCookie = new Cookie("jwt", "malformed_or_expired_token");
        when(request.getCookies()).thenReturn(new Cookie[]{jwtCookie});
        when(jwtUtil.extractClaim(eq("malformed_or_expired_token"), any())).thenThrow(new JwtException("Expired token"));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), contains("Max-Age=0"));
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
