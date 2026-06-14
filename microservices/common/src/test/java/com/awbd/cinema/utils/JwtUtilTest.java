package com.awbd.cinema.utils;

import com.awbd.cinema.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        ReflectionTestUtils.setField(jwtUtil, "secretKey", testSecretKey);
    }

    @Test
    void generateToken_ShouldGenerateValidAccessTokenWithClaims() {
        String username = "cinema_fan";

        String token = jwtUtil.generateToken(username, 42L, Role.USER);

        assertThat(token).isNotNull();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo(username);
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtUtil.extractRole(token)).isEqualTo(Role.USER);
        assertThat(jwtUtil.extractExpiration(token)).isAfter(new Date());

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

        String type = jwtUtil.extractClaim(token, claims -> claims.get("typ", String.class));
        assertThat(type).isEqualTo("REFRESH");
    }

    @Test
    void isTokenValid_ShouldReturnTrue_WhenUsernameMatchesAndTokenNotExpired() {
        String username = "validUser";
        String token = jwtUtil.generateToken(username, 1L, Role.USER);

        boolean isValid = jwtUtil.isTokenValid(token, username);

        assertThat(isValid).isTrue();
    }

    @Test
    void isTokenValid_ShouldReturnFalse_WhenUsernameDoesNotMatch() {
        String username = "validUser";
        String token = jwtUtil.generateToken(username, 1L, Role.USER);

        boolean isValid = jwtUtil.isTokenValid(token, "intruderUser");

        assertThat(isValid).isFalse();
    }
}
