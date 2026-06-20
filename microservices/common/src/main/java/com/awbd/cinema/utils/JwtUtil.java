package com.awbd.cinema.utils;

import com.awbd.cinema.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtUtil {

    @Value("${jwt.secret.key}")
    private String secretKey;

    @Value("${service.token.ttl-seconds:60}")
    private long serviceTokenTtlSeconds;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, Long userId, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("typ", "ACCESS")
                .claim("userId", userId)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(3, ChronoUnit.HOURS)))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("typ", "REFRESH")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(30, ChronoUnit.DAYS)))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateServiceToken(String serviceName) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(serviceName)
                .claim("typ", "SERVICE")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(serviceTokenTtlSeconds)))
                .signWith(getSigningKey())
                .compact();
    }

    public String validateServiceToken(String token) {
        // extractClaim parses & verifies the signature and throws on expiry.
        String type = extractClaim(token, claims -> claims.get("typ", String.class));
        if (!"SERVICE".equals(type)) {
            throw new JwtException("Token is not a service token");
        }
        return extractClaim(token, Claims::getSubject);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public Role extractRole(String token) {
        String role = extractClaim(token, claims -> claims.get("role", String.class));
        return role == null ? null : Role.valueOf(role);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims =
                Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
        return claimsResolver.apply(claims);
    }

    public boolean isTokenValid(String token, String username) {
        return username.equals(extractUsername(token)) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}
