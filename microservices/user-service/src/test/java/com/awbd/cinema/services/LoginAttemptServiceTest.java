package com.awbd.cinema.services;

import com.awbd.cinema.services.LoginAttemptService.LoginAttemptServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private LoginAttemptServiceImpl loginAttemptService;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptServiceImpl();

        // Inject properties normally filled by @Value
        ReflectionTestUtils.setField(loginAttemptService, "MAX_ATTEMPT", 3);
        ReflectionTestUtils.setField(loginAttemptService, "trustForwardedHeaders", false);

        // Bind mock HttpServletRequest to the execution thread context
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    @AfterEach
    void tearDown() {
        // Prevent state leaking across test runs
        RequestContextHolder.resetRequestAttributes();
    }

    // ==========================================
    // ACCOUNT BLOCK & LOCKOUT SCENARIOS
    // ==========================================
    @Nested
    @DisplayName("Login State Actions and Lockout Logic")
    class LockoutScenarios {

        @Test
        @DisplayName("Should track incrementing failure iterations and block user after threshold maximum reached")
        void loginFailed_IncrementsCounter_BlocksOnThreshold() {
            String username = "attacker_user";
            when(request.getRemoteAddr()).thenReturn("192.168.1.100");

            // First Fail
            loginAttemptService.loginFailed(username);
            assertThat(loginAttemptService.isBlocked(username)).isFalse();

            // Second Fail
            loginAttemptService.loginFailed(username);
            assertThat(loginAttemptService.isBlocked(username)).isFalse();

            // Third Fail (Hits maximum threshold of 3)
            loginAttemptService.loginFailed(username);
            assertThat(loginAttemptService.isBlocked(username)).isTrue();
        }

        @Test
        @DisplayName("Should clear tracking block logs immediately upon a successful login verification")
        void loginSucceeded_ClearsCacheCounter() {
            String username = "forgetful_user";
            when(request.getRemoteAddr()).thenReturn("192.168.1.100");

            loginAttemptService.loginFailed(username);
            loginAttemptService.loginFailed(username);

            // Success call resets tracking counter completely
            loginAttemptService.loginSucceeded(username);

            assertThat(loginAttemptService.isBlocked(username)).isFalse();
        }

        @Test
        @DisplayName("Should handle username trimming and lowercase normalization safely")
        void tracking_NormalizesUsernameCaseAndSpaces() {
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");

            loginAttemptService.loginFailed("  UserNAME  ");
            loginAttemptService.loginFailed("username");
            loginAttemptService.loginFailed("USERNAME");

            assertThat(loginAttemptService.isBlocked("username")).isTrue();
            assertThat(loginAttemptService.isBlocked("  Username  ")).isTrue();
        }
    }

    // ==========================================
    // IP HANDLING & HEADER PARSING
    // ==========================================
    @Nested
    @DisplayName("Client IP Parsing Strategies")
    class ClientIpResolution {

        @Test
        @DisplayName("Should ignore proxy headers entirely and use remote address when trust parameters are disabled")
        void getClientIP_TrustDisabled_ReturnsRemoteAddress() {
            ReflectionTestUtils.setField(loginAttemptService, "trustForwardedHeaders", false);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            loginAttemptService.loginFailed("user");

            // Extract the internally compiled cache key directly to verify resolution path matches raw remote address
            Cache<String, Integer> cache = (Cache<String, Integer>) ReflectionTestUtils.getField(loginAttemptService, "attemptsCache");
            assertThat(cache.asMap()).containsKey("user:127.0.0.1");
        }

        @Test
        @DisplayName("Should prioritize CF-Connecting-IP header value when trust parameters are enabled")
        void getClientIP_TrustEnabled_PrioritizesCloudflareHeader() {
            ReflectionTestUtils.setField(loginAttemptService, "trustForwardedHeaders", true);
            when(request.getHeader("CF-Connecting-IP")).thenReturn("203.0.113.195");

            loginAttemptService.loginFailed("user");

            Cache<String, Integer> cache = (Cache<String, Integer>) ReflectionTestUtils.getField(loginAttemptService, "attemptsCache");
            assertThat(cache.asMap()).containsKey("user:203.0.113.195");
        }

        @Test
        @DisplayName("Should split X-Forwarded-For value and pick the first array element entry accurately")
        void getClientIP_TrustEnabled_ParsesFirstElementFromXForwardedFor() {
            ReflectionTestUtils.setField(loginAttemptService, "trustForwardedHeaders", true);
            when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
            when(request.getHeader("X-Forwarded-For")).thenReturn(" 198.51.100.42, 192.168.1.1, 10.0.0.1 ");

            loginAttemptService.loginFailed("user");

            Cache<String, Integer> cache = (Cache<String, Integer>) ReflectionTestUtils.getField(loginAttemptService, "attemptsCache");
            assertThat(cache.asMap()).containsKey("user:198.51.100.42");
        }

        @Test
        @DisplayName("Should pick X-Real-IP value when preceding proxy headers are completely empty")
        void getClientIP_TrustEnabled_FallbackToXRealIp() {
            ReflectionTestUtils.setField(loginAttemptService, "trustForwardedHeaders", true);
            when(request.getHeader("CF-Connecting-IP")).thenReturn("");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn("172.16.254.1");

            loginAttemptService.loginFailed("user");

            Cache<String, Integer> cache = (Cache<String, Integer>) ReflectionTestUtils.getField(loginAttemptService, "attemptsCache");
            assertThat(cache.asMap()).containsKey("user:172.16.254.1");
        }

        @Test
        @DisplayName("Should return unknown when execution runs completely outside an HTTP lifecycle thread constraint")
        void getClientIP_NoCurrentRequest_ReturnsUnknown() {
            RequestContextHolder.resetRequestAttributes(); // Remove mock context completely

            loginAttemptService.loginFailed("user");

            Cache<String, Integer> cache = (Cache<String, Integer>) ReflectionTestUtils.getField(loginAttemptService, "attemptsCache");
            assertThat(cache.asMap()).containsKey("user:unknown");
        }
    }
}
