package com.awbd.cinema.services;

import com.awbd.cinema.DTOs.AuthDTOs.LoginActionDTO;
import com.awbd.cinema.DTOs.AuthDTOs.LoginCookiesDTO;
import com.awbd.cinema.DTOs.AuthDTOs.LoginDTO;
import com.awbd.cinema.DTOs.AuthDTOs.RegisterDTO;
import com.awbd.cinema.DTOs.AuthDTOs.RegisterResponseDTO;
import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.clients.BookingServiceGateway;
import com.awbd.cinema.entities.User;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.InvalidFieldException;
import com.awbd.cinema.exceptions.TooManyRequestsException;
import com.awbd.cinema.exceptions.UnauthenticatedException;
import com.awbd.cinema.repositories.UserRepository;
import com.awbd.cinema.services.AuthService.AuthServiceImpl;
import com.awbd.cinema.services.LoginAttemptService.LoginAttemptService;
import com.awbd.cinema.utils.JwtUtil;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private BookingServiceGateway bookingServiceGateway;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks private AuthServiceImpl authService;

    private RegisterDTO sampleRegisterDTO;
    private LoginDTO sampleLoginDTO;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "cookieSecure", true);
        ReflectionTestUtils.setField(authService, "cookieSameSite", "Strict");

        sampleRegisterDTO = new RegisterDTO(
                "testuser", "Password123!", "Password123!",
                "test@example.com", "John", "Doe", "+1234567890"
        );

        sampleLoginDTO = new LoginDTO("testuser", "Password123!");
    }

    @Nested
    class RegisterTests {

        @Test
        void register_Success() {
            when(userRepository.existsUserByUsernameIgnoreCaseOrEmailIgnoreCase(anyString(), anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");

            User savedUser = User.builder()
                    .id(10L)
                    .username("testuser")
                    .email("test@example.com")
                    .firstName("John")
                    .lastName("Doe")
                    .build();
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            RegisterResponseDTO response = authService.register(sampleRegisterDTO);

            assertNotNull(response);
            assertEquals("Account created successfully.", response.message());
            assertEquals("testuser", response.username());

            ArgumentCaptor<CreateNotificationDTO> notificationCaptor = ArgumentCaptor.forClass(CreateNotificationDTO.class);
            verify(bookingServiceGateway, times(1)).createNotification(notificationCaptor.capture());

            CreateNotificationDTO sentNotification = notificationCaptor.getValue();
            assertEquals(NotificationType.EMAIL_VERIFICATION, sentNotification.type());
            assertEquals(10L, sentNotification.userId());
            assertTrue(sentNotification.content().contains("test@example.com"));
        }

        @Test
        void register_ThrowsAlreadyExistsException_WhenUserOrEmailExists() {
            when(userRepository.existsUserByUsernameIgnoreCaseOrEmailIgnoreCase(sampleRegisterDTO.username(), sampleRegisterDTO.email()))
                    .thenReturn(true);

            assertThrows(AlreadyExistsException.class, () -> authService.register(sampleRegisterDTO));
            verify(userRepository, never()).save(any(User.class));
            verify(bookingServiceGateway, never()).createNotification(any(CreateNotificationDTO.class));
        }
    }

    @Nested
    class LoginTests {

        @Test
        void login_ThrowsTooManyRequestsException_WhenBlocked() {
            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(true);

            assertThrows(TooManyRequestsException.class, () -> authService.login(sampleLoginDTO));
            verify(authenticationManager, never()).authenticate(any());
        }

        @Test
        void login_ThrowsInvalidFieldException_OnBadCredentials() {
            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThrows(InvalidFieldException.class, () -> authService.login(sampleLoginDTO));
            verify(loginAttemptService, times(1)).loginFailed(sampleLoginDTO.username());
        }

        @Test
        void login_ThrowsTooManyRequestsException_OnLockedException() {
            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new LockedException("Locked"));

            assertThrows(TooManyRequestsException.class, () -> authService.login(sampleLoginDTO));
        }

        @Test
        void login_ThrowsInvalidFieldException_OnDisabledException() {
            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new DisabledException("Disabled"));

            assertThrows(InvalidFieldException.class, () -> authService.login(sampleLoginDTO));
        }

        @Test
        void login_ThrowsInvalidFieldException_WhenUserNotFoundInDatabase() {
            Authentication mockAuth = mock(Authentication.class);
            when(mockAuth.getName()).thenReturn("testuser");

            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(mockAuth);
            when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.empty());

            assertThrows(InvalidFieldException.class, () -> authService.login(sampleLoginDTO));
        }

        @Test
        void login_ThrowsInvalidFieldException_WhenUserIsSoftDeleted() {
            Authentication mockAuth = mock(Authentication.class);
            when(mockAuth.getName()).thenReturn("testuser");

            User softDeletedUser = User.builder()
                    .username("testuser")
                    .deletedAt(LocalDateTime.now())
                    .build();

            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(mockAuth);
            when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(softDeletedUser));

            assertThrows(InvalidFieldException.class, () -> authService.login(sampleLoginDTO));
            verify(loginAttemptService, times(1)).loginFailed(sampleLoginDTO.username());
        }

        @Test
        void login_Success() {
            Authentication mockAuth = mock(Authentication.class);
            when(mockAuth.getName()).thenReturn("testuser");

            User activeUser = User.builder()
                    .id(1L)
                    .username("testuser")
                    .email("test@example.com")
                    .firstName("John")
                    .lastName("Doe")
                    .role(Role.USER)
                    .deletedAt(null)
                    .build();

            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(mockAuth);
            when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(activeUser));

            when(jwtUtil.generateToken("testuser", 1L, Role.USER)).thenReturn("mock-jwt-token");
            when(jwtUtil.generateRefreshToken("testuser")).thenReturn("mock-refresh-token");

            LoginActionDTO result = authService.login(sampleLoginDTO);

            assertNotNull(result);
            assertEquals("testuser", result.response().username());

            ResponseCookie jwtCookie = result.cookies().jwtCookie();
            assertEquals("jwt", jwtCookie.getName());
            assertEquals("mock-jwt-token", jwtCookie.getValue());
            assertTrue(jwtCookie.isSecure());
            assertTrue(jwtCookie.isHttpOnly());
            assertEquals("Strict", jwtCookie.getSameSite());

            ResponseCookie refreshCookie = result.cookies().refreshTokenCookie();
            assertEquals("refresh", refreshCookie.getName());
            assertEquals("mock-refresh-token", refreshCookie.getValue());

            verify(loginAttemptService, times(1)).loginSucceeded("testuser");
        }
    }

    @Nested
    class CreateOwnerTests {

        @Test
        void createOwner_DoesNotSave_IfOwnerAlreadyExists() {
            User existingUser = User.builder().username("testuser").email("test@example.com").build();
            when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(sampleRegisterDTO.username(), sampleRegisterDTO.email()))
                    .thenReturn(Optional.of(existingUser));

            authService.createOwner(sampleRegisterDTO);

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void createOwner_SavesNewUserAsOwner_IfNotFound() {
            when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(sampleRegisterDTO.username(), sampleRegisterDTO.email()))
                    .thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");

            User savedOwner = User.builder()
                    .username("testuser")
                    .role(Role.OWNER)
                    .build();
            when(userRepository.save(any(User.class))).thenReturn(savedOwner);

            authService.createOwner(sampleRegisterDTO);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(1)).save(userCaptor.capture());

            User executedUserMapping = userCaptor.getValue();
            assertEquals(Role.OWNER, executedUserMapping.getRole());
            assertEquals("testuser", executedUserMapping.getUsername());
        }
    }

    @Nested
    class RefreshTokensTests {

        @Test
        void refreshTokens_Success() {
            // Arrange
            when(jwtUtil.extractClaim(eq("valid-refresh-token"), any())).thenReturn("REFRESH");
            when(jwtUtil.extractUsername("valid-refresh-token")).thenReturn("testuser");

            User activeUser = User.builder()
                    .id(1L)
                    .username("testuser")
                    .email("test@example.com")
                    .role(Role.USER)
                    .deletedAt(null)
                    .build();
            when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(activeUser));

            when(jwtUtil.generateToken("testuser", 1L, Role.USER)).thenReturn("new-jwt-token");
            when(jwtUtil.generateRefreshToken("testuser")).thenReturn("new-refresh-token");

            // Act
            LoginCookiesDTO result = authService.refreshTokens("valid-refresh-token");

            // Assert
            assertNotNull(result);
            assertEquals("jwt", result.jwtCookie().getName());
            assertEquals("new-jwt-token", result.jwtCookie().getValue());
            assertTrue(result.jwtCookie().isSecure());
            assertTrue(result.jwtCookie().isHttpOnly());

            assertEquals("refresh", result.refreshTokenCookie().getName());
            assertEquals("new-refresh-token", result.refreshTokenCookie().getValue());
        }

        @Test
        void refreshTokens_ThrowsUnauthenticated_WhenTokenTypeNotRefresh() {
            // Arrange
            when(jwtUtil.extractClaim(eq("access-token"), any())).thenReturn("ACCESS");

            // Act & Assert
            assertThrows(UnauthenticatedException.class, () -> authService.refreshTokens("access-token"));
            verify(userRepository, never()).findByUsernameIgnoreCase(any());
        }

        @Test
        void refreshTokens_ThrowsUnauthenticated_WhenTokenInvalid() {
            // Arrange
            when(jwtUtil.extractClaim(eq("expired-token"), any())).thenThrow(new JwtException("Expired"));

            // Act & Assert
            assertThrows(UnauthenticatedException.class, () -> authService.refreshTokens("expired-token"));
            verify(userRepository, never()).findByUsernameIgnoreCase(any());
        }

        @Test
        void refreshTokens_ThrowsUnauthenticated_WhenUserNotFound() {
            // Arrange
            when(jwtUtil.extractClaim(eq("valid-refresh-token"), any())).thenReturn("REFRESH");
            when(jwtUtil.extractUsername("valid-refresh-token")).thenReturn("nonexistent");
            when(userRepository.findByUsernameIgnoreCase("nonexistent")).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(UnauthenticatedException.class, () -> authService.refreshTokens("valid-refresh-token"));
        }

        @Test
        void refreshTokens_ThrowsUnauthenticated_WhenUserSoftDeleted() {
            // Arrange
            when(jwtUtil.extractClaim(eq("valid-refresh-token"), any())).thenReturn("REFRESH");
            when(jwtUtil.extractUsername("valid-refresh-token")).thenReturn("testuser");

            User softDeletedUser = User.builder()
                    .username("testuser")
                    .deletedAt(LocalDateTime.now())
                    .build();
            when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(softDeletedUser));

            // Act & Assert
            assertThrows(UnauthenticatedException.class, () -> authService.refreshTokens("valid-refresh-token"));
        }
    }

    @Nested
    class LogoutTests {

        @Test
        void logoutCookies_ReturnsExpiredCookies() {
            // Act
            LoginCookiesDTO result = authService.logoutCookies();

            // Assert
            assertNotNull(result);

            ResponseCookie jwtCookie = result.jwtCookie();
            assertEquals("jwt", jwtCookie.getName());
            assertEquals("", jwtCookie.getValue());
            assertEquals(0, jwtCookie.getMaxAge().getSeconds());
            assertTrue(jwtCookie.isSecure());
            assertTrue(jwtCookie.isHttpOnly());

            ResponseCookie refreshCookie = result.refreshTokenCookie();
            assertEquals("refresh", refreshCookie.getName());
            assertEquals("", refreshCookie.getValue());
            assertEquals(0, refreshCookie.getMaxAge().getSeconds());
            assertTrue(refreshCookie.isSecure());
            assertTrue(refreshCookie.isHttpOnly());
        }
    }
}
