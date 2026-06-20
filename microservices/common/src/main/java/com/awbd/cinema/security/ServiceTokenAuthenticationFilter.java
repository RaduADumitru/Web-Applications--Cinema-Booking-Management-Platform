package com.awbd.cinema.security;

import com.awbd.cinema.utils.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                String serviceName = jwtUtil.validateServiceToken(token);
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                serviceName, null, List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.info("Internal request authenticated as service '{}' -> {} {}",
                        serviceName, request.getMethod(), request.getRequestURI());
            } catch (JwtException e) {
                log.warn("Rejecting internal request with invalid service token: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
