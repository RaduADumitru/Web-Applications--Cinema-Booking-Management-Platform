package com.awbd.cinema.services.LoginAttemptService;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

    @Value("${security.max-attempts}")
    private int MAX_ATTEMPT;

    @Value("${security.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    private final Cache<String, Integer> attemptsCache;

    public LoginAttemptServiceImpl() {
        this.attemptsCache = Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
    }

    @Override
    public void loginFailed(String username) {
        String key = getCacheKey(username);
        attemptsCache.asMap().compute(key, (k, v) -> (v == null) ? 1 : v + 1);
    }

    @Override
    public void loginSucceeded(String username) {
        attemptsCache.invalidate(getCacheKey(username));
    }

    @Override
    public boolean isBlocked(String username) {
        return attemptsCache.get(getCacheKey(username), k -> 0) >= MAX_ATTEMPT;
    }

    private String getCacheKey(String username) {
        String normalizedUsername = (username == null) ? "" : username.trim().toLowerCase(Locale.ROOT);
        return normalizedUsername + ":" + getClientIP();
    }

    private String getClientIP() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return "unknown";
        }

        if (!trustForwardedHeaders) {
            return request.getRemoteAddr();
        }
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isBlank()) {
            return cfConnectingIp.trim();
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    private HttpServletRequest getCurrentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }
}
