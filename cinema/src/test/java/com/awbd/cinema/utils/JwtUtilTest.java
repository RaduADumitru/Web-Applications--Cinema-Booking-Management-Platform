package com.awbd.cinema.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    // HS256 algorithms require a secret key of at least 256 bits (32 bytes)
    private final String testSecretKey = "superSecretKeyForCinemaApplication2026!!";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // Inject the private @Value("${jwt.secret.key}") field using reflection
        ReflectionTestUtils.setField(jwtUtil, "secretKey", testSecretKey);
        // Ensure clean slate for security context before each test
        SecurityContextHolder.clearContext();
    }

    @Test
    void generateToken_ShouldGenerateValidAccessToken() {
        String username = "cinema_fan";

        String token = jwtUtil.generateToken(username);

        assertThat(token).isNotNull();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo(username);
        assertThat(jwtUtil.extractExpiration(token)).isAfter(new Date());

        // Verifying the custom claim type
        String type = jwtUtil.extractClaim(token, claims -> claims.get("typ", String.class));
        assertThat(type).isEqualTo("ACCESS");
    }

    @Test
    void generateRefreshToken_ShouldGenerateValidRefreshToken() {
        String username = "admin_user";

        String token = jwtUtil.generateRefreshToken(username);

        assertThat(token).isNotNull();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo(username);
        assertThat(jwtUtil.extractExpiration(token)).isAfter(new Date());

        // Verifying the custom claim type
        String type = jwtUtil.extractClaim(token, claims -> claims.get("typ", String.class));
        assertThat(type).isEqualTo("REFRESH");
    }

    @Test
    void isTokenValid_ShouldReturnTrue_WhenUsernameMatchesAndTokenNotExpired() {
        String username = "validUser";
        String token = jwtUtil.generateToken(username);

        boolean isValid = jwtUtil.isTokenValid(token, username);

        assertThat(isValid).isTrue();
    }

    @Test
    void isTokenValid_ShouldReturnFalse_WhenUsernameDoesNotMatch() {
        String username = "validUser";
        String token = jwtUtil.generateToken(username);

        // This triggers the false side of the username comparison branch
        boolean isValid = jwtUtil.isTokenValid(token, "intruderUser");

        assertThat(isValid).isFalse();
    }

    @Test
    void extractUsername_FromSecurityContext_ShouldReturnCurrentAuthenticatedName() {
        // Mock the Spring Security Architecture
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getName()).thenReturn("securityPrincipal");

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);

        String currentUsername = jwtUtil.extractUsername();

        assertThat(currentUsername).isEqualTo("securityPrincipal");
    }
}