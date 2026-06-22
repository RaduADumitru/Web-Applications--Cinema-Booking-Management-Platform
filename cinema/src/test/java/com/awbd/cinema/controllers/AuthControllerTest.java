package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.AuthDTOs.*;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.security.CustomUserDetailsService;
import com.awbd.cinema.services.AuthService.AuthService;
import com.awbd.cinema.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private CookieCsrfTokenRepository csrfTokenRepository;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private RegisterDTO validRegisterDTO;
    private LoginDTO validLoginDTO;

    @BeforeEach
    void setUp() {
        validRegisterDTO = new RegisterDTO(
                "testuser", "Password123!", "Password123!",
                "test@example.com", "John", "Doe", "+1234567890"
        );

        validLoginDTO = new LoginDTO("testuser", "Password123!");
    }

    @Nested
    class RegisterEndpointTests {

        @Test
        void register_WithValidPayload_ReturnsCreated() throws Exception {
            // Arrange
            RegisterResponseDTO expectedResponse = new RegisterResponseDTO("Account created successfully.", "testuser");
            when(authService.register(any(RegisterDTO.class))).thenReturn(expectedResponse);

            // Act & Assert
            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRegisterDTO)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("Account created successfully."))
                    .andExpect(jsonPath("$.username").value("testuser"));

            verify(authService, times(1)).register(any(RegisterDTO.class));
        }

        @Test
        void register_WithInvalidPayload_ReturnsBadRequest() throws Exception {
            // Arrange - invalid username and invalid password according to annotations
            RegisterDTO invalidDTO = new RegisterDTO(
                    "", "short", "short",
                    "not-an-email", "", "", ""
            );

            // Act & Assert (Validation intercepts before the service is even called)
            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidDTO)))
                    .andExpect(status().isUnprocessableContent());

            verify(authService, never()).register(any());
        }
    }

    @Nested
    class LoginEndpointTests {

        @Test
        void login_WithValidCredentials_ReturnsOkWithCookiesAndBody() throws Exception {
            // Arrange
            LoginResponseDTO responseDTO = LoginResponseDTO.builder()
                    .username("testuser")
                    .email("test@example.com")
                    .firstName("John")
                    .lastName("Doe")
                    .role(Role.USER)
                    .build();

            ResponseCookie jwtCookie = ResponseCookie.from("jwt", "mocked-jwt-token").path("/").build();
            ResponseCookie refreshCookie = ResponseCookie.from("refresh", "mocked-refresh-token").path("/").build();
            LoginCookiesDTO cookiesDTO = new LoginCookiesDTO(jwtCookie, refreshCookie);

            LoginActionDTO actionDTO = new LoginActionDTO(responseDTO, cookiesDTO);

            // Mocking Service
            when(authService.login(any(LoginDTO.class))).thenReturn(actionDTO);

            // Mocking CSRF Generation
            CsrfToken mockCsrfToken = mock(CsrfToken.class);
            when(csrfTokenRepository.generateToken(any(HttpServletRequest.class))).thenReturn(mockCsrfToken);
            doNothing().when(csrfTokenRepository).saveToken(any(CsrfToken.class), any(HttpServletRequest.class), any(HttpServletResponse.class));

            // Act & Assert
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validLoginDTO)))
                    .andExpect(status().isOk())
                    .andExpect(cookie().value("jwt", "mocked-jwt-token"))
                    .andExpect(cookie().path("jwt", "/"))
                    .andExpect(cookie().value("refresh", "mocked-refresh-token"))
                    .andExpect(cookie().path("refresh", "/"))
                    .andExpect(jsonPath("$.username").value("testuser"))
                    .andExpect(jsonPath("$.email").value("test@example.com"))
                    .andExpect(jsonPath("$.role").value("USER"));

            verify(authService, times(1)).login(any(LoginDTO.class));
            verify(csrfTokenRepository, times(1)).generateToken(any(HttpServletRequest.class));
            verify(csrfTokenRepository, times(1)).saveToken(any(), any(), any());
        }

        @Test
        void login_WithMissingFields_ReturnsBadRequest() throws Exception {
            // Arrange - empty payload failing @NotEmpty / @NotBlank
            LoginDTO invalidLogin = new LoginDTO("", "   ");

            // Act & Assert
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidLogin)))
                    .andExpect(status().isUnprocessableContent());

            verify(authService, never()).login(any());
        }
    }

    @Nested
    class RefreshEndpointTests {

        @Test
        void refresh_WithValidCookie_ReturnsOkWithNewCookies() throws Exception {
            // Arrange
            ResponseCookie jwtCookie = ResponseCookie.from("jwt", "new-jwt-token").path("/").build();
            ResponseCookie refreshCookie = ResponseCookie.from("refresh", "new-refresh-token").path("/").build();
            LoginCookiesDTO cookiesDTO = new LoginCookiesDTO(jwtCookie, refreshCookie);

            when(authService.refreshTokens("mock-refresh-token")).thenReturn(cookiesDTO);

            // Act & Assert
            mockMvc.perform(post("/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie("refresh", "mock-refresh-token")))
                    .andExpect(status().isOk())
                    .andExpect(cookie().value("jwt", "new-jwt-token"))
                    .andExpect(cookie().value("refresh", "new-refresh-token"))
                    .andExpect(jsonPath("$.message").value("Token refreshed successfully."));

            verify(authService, times(1)).refreshTokens("mock-refresh-token");
        }

        @Test
        void refresh_WithoutCookie_ReturnsBadRequest() throws Exception {
            // Act & Assert — no refresh cookie sent
            mockMvc.perform(post("/auth/refresh"))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).refreshTokens(any());
        }
    }

    @Nested
    class LogoutEndpointTests {

        @Test
        void logout_ReturnsOkWithExpiredCookies() throws Exception {
            // Arrange
            ResponseCookie expiredJwt = ResponseCookie.from("jwt", "").path("/").maxAge(0).build();
            ResponseCookie expiredRefresh = ResponseCookie.from("refresh", "").path("/").maxAge(0).build();
            LoginCookiesDTO cookiesDTO = new LoginCookiesDTO(expiredJwt, expiredRefresh);

            when(authService.logoutCookies()).thenReturn(cookiesDTO);

            // Act & Assert
            mockMvc.perform(post("/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(cookie().value("jwt", ""))
                    .andExpect(cookie().maxAge("jwt", 0))
                    .andExpect(cookie().value("refresh", ""))
                    .andExpect(cookie().maxAge("refresh", 0))
                    .andExpect(jsonPath("$.message").value("Logged out successfully."));

            verify(authService, times(1)).logoutCookies();
        }
    }
}