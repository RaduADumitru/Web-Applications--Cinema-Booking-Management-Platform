package com.awbd.cinema.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import com.awbd.cinema.DTOs.AuthDTOs.LoginActionDTO;
import com.awbd.cinema.DTOs.AuthDTOs.LoginDTO;
import com.awbd.cinema.DTOs.AuthDTOs.RegisterDTO;
import com.awbd.cinema.DTOs.AuthDTOs.RegisterResponseDTO;
import com.awbd.cinema.entities.Notification;
import com.awbd.cinema.entities.User;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.InvalidFieldException;
import com.awbd.cinema.exceptions.TooManyRequestsException;
import com.awbd.cinema.repositories.NotificationRepository;
import com.awbd.cinema.repositories.UserRepository;
import com.awbd.cinema.services.AuthService.AuthServiceImpl;
import com.awbd.cinema.services.LoginAttemptService.LoginAttemptService;
import com.awbd.cinema.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks private AuthServiceImpl authService;

    private RegisterDTO sampleRegisterDTO;
    private LoginDTO sampleLoginDTO;

    @BeforeEach
    void setUp() {
        // Injecting the @Value fields
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
            // Arrange
            when(userRepository.existsUserByUsernameIgnoreCaseOrEmailIgnoreCase(anyString(), anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");

            User savedUser = User.builder()
                    .username("testuser")
                    .email("test@example.com")
                    .firstName("John")
                    .lastName("Doe")
                    .build();
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // Act
            RegisterResponseDTO response = authService.register(sampleRegisterDTO);

            // Assert
            assertNotNull(response);
            assertEquals("Account created successfully.", response.message());
            assertEquals("testuser", response.username());

            // Verify notification generation
            ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, times(1)).save(notificationCaptor.capture());

            Notification savedNotification = notificationCaptor.getValue();
            assertEquals(NotificationType.EMAIL_VERIFICATION, savedNotification.getType());
            assertTrue(savedNotification.getContent().contains("test@example.com"));
        }

        @Test
        void register_ThrowsAlreadyExistsException_WhenUserOrEmailExists() {
            // Arrange
            when(userRepository.existsUserByUsernameIgnoreCaseOrEmailIgnoreCase(sampleRegisterDTO.username(), sampleRegisterDTO.email()))
                    .thenReturn(true);

            // Act & Assert
            assertThrows(AlreadyExistsException.class, () -> authService.register(sampleRegisterDTO));
            verify(userRepository, never()).save(any(User.class));
            verify(notificationRepository, never()).save(any(Notification.class));
        }
    }

    @Nested
    class LoginTests {

        @Test
        void login_ThrowsTooManyRequestsException_WhenBlocked() {
            // Arrange
            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(true);

            // Act & Assert
            assertThrows(TooManyRequestsException.class, () -> authService.login(sampleLoginDTO));
            verify(authenticationManager, never()).authenticate(any());
        }

        @Test
        void login_ThrowsInvalidFieldException_OnBadCredentials() {
            // Arrange
            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            // Act & Assert
            assertThrows(InvalidFieldException.class, () -> authService.login(sampleLoginDTO));
            verify(loginAttemptService, times(1)).loginFailed(sampleLoginDTO.username());
        }

        @Test
        void login_ThrowsTooManyRequestsException_OnLockedException() {
            // Arrange
            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new LockedException("Locked"));

            // Act & Assert
            assertThrows(TooManyRequestsException.class, () -> authService.login(sampleLoginDTO));
        }

        @Test
        void login_ThrowsInvalidFieldException_OnDisabledException() {
            // Arrange
            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new DisabledException("Disabled"));

            // Act & Assert
            assertThrows(InvalidFieldException.class, () -> authService.login(sampleLoginDTO));
        }

        @Test
        void login_ThrowsInvalidFieldException_WhenUserNotFoundInDatabase() {
            // Arrange
            Authentication mockAuth = mock(Authentication.class);
            when(mockAuth.getName()).thenReturn("testuser");

            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(mockAuth);
            when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(InvalidFieldException.class, () -> authService.login(sampleLoginDTO));
        }

        @Test
        void login_ThrowsInvalidFieldException_WhenUserIsSoftDeleted() {
            // Arrange
            Authentication mockAuth = mock(Authentication.class);
            when(mockAuth.getName()).thenReturn("testuser");

            User softDeletedUser = User.builder()
                    .username("testuser")
                    .deletedAt(LocalDateTime.now())
                    .build();

            when(loginAttemptService.isBlocked(sampleLoginDTO.username())).thenReturn(false);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(mockAuth);
            when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(softDeletedUser));

            // Act & Assert
            assertThrows(InvalidFieldException.class, () -> authService.login(sampleLoginDTO));
            verify(loginAttemptService, times(1)).loginFailed(sampleLoginDTO.username());
        }

        @Test
        void login_Success() {
            // Arrange
            Authentication mockAuth = mock(Authentication.class);
            when(mockAuth.getName()).thenReturn("testuser");

            User activeUser = User.builder()
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

            when(jwtUtil.generateToken("testuser")).thenReturn("mock-jwt-token");
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
            // Arrange
            User existingUser = User.builder().username("testuser").email("test@example.com").build();
            when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(sampleRegisterDTO.username(), sampleRegisterDTO.email()))
                    .thenReturn(Optional.of(existingUser));

            // Act
            authService.createOwner(sampleRegisterDTO);

            // Assert
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void createOwner_SavesNewUserAsOwner_IfNotFound() {
            // Arrange
            when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(sampleRegisterDTO.username(), sampleRegisterDTO.email()))
                    .thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");

            User savedOwner = User.builder()
                    .username("testuser")
                    .role(Role.OWNER)
                    .build();
            when(userRepository.save(any(User.class))).thenReturn(savedOwner);

            // Act
            authService.createOwner(sampleRegisterDTO);

            // Assert
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(1)).save(userCaptor.capture());

            User executedUserMapping = userCaptor.getValue();
            assertEquals(Role.OWNER, executedUserMapping.getRole());
            assertEquals("testuser", executedUserMapping.getUsername());
        }
    }
}