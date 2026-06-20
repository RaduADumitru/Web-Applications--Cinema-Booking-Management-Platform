package com.awbd.cinema.controllers;

import com.awbd.cinema.config.RoleHierarchyConfig;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.security.CustomUserDetails;
import com.awbd.cinema.security.JwtAuthenticationFilter;
import com.awbd.cinema.security.SecurityConfig;
import com.awbd.cinema.utils.JwtUtil;
import com.awbd.cinema.utils.SecurityCorsProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@WebMvcTest
@Import({SecurityConfig.class, RoleHierarchyConfig.class})
public abstract class BaseControllerTest {

    @MockitoBean
    protected JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    protected SecurityCorsProperties securityCorsProperties;

    @MockitoBean
    protected JwtUtil jwtUtil;

    @BeforeEach
    void setupMockFilters() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    protected CustomUserDetails loginAsDefaultUser() {
        return loginAs(1L, "test_user", Role.USER);
    }

    protected CustomUserDetails loginAs(Long userId, String username, Role role) {
        CustomUserDetails userDetails =
                new CustomUserDetails(userId, username, "password123", role, null);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        return userDetails;
    }
}
