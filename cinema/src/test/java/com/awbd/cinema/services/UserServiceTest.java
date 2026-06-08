package com.awbd.cinema.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.awbd.cinema.DTOs.UserDTOs.ProfileDTO;
import com.awbd.cinema.DTOs.UserDTOs.PromoteDTO;
import com.awbd.cinema.DTOs.UserDTOs.UpdateProfileDTO;
import com.awbd.cinema.entities.User;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.UserRepository;
import com.awbd.cinema.security.CustomUserDetails;
import com.awbd.cinema.services.UserService.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private UserServiceImpl userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .username("john_doe")
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+123456789")
                .loyaltyPoints(10)
                .role(Role.USER)
                .deletedAt(null)
                .build();
    }

    @Nested
    class GetProfileTests {

        @Test
        void getProfile_Success() {
            // Arrange
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

            // Act
            ProfileDTO result = userService.getProfile(1L);

            // Assert
            assertNotNull(result);
            assertEquals("john_doe", result.username());
            assertEquals(10, result.loyaltyPoints());
        }

        @Test
        void getProfile_ThrowsNotFoundException_WhenUserDoesNotExist() {
            // Arrange
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () -> userService.getProfile(1L));
        }
    }

    @Nested
    class DeleteAccountWithUserDetailsTests {

        @Test
        void deleteAccount_ThrowsBadRequestException_WhenUserIsOwner() {
            // Arrange
            CustomUserDetails mockDetails = mock(CustomUserDetails.class);
            GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_OWNER");

            // Mocking the wildcard collection elements safely
            doReturn(List.of(authority)).when(mockDetails).getAuthorities();

            // Act & Assert
            assertThrows(BadRequestException.class, () -> userService.deleteAccount(mockDetails));
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void deleteAccount_Success_WhenUserIsNonOwner() {
            // Arrange
            CustomUserDetails mockDetails = mock(CustomUserDetails.class);
            GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_USER");

            doReturn(List.of(authority)).when(mockDetails).getAuthorities();
            when(mockDetails.getId()).thenReturn(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

            // Act
            Map<String, String> response = userService.deleteAccount(mockDetails);

            // Assert
            assertNotNull(response);
            assertEquals("Your account has been deleted successfully.", response.get("message"));
            verify(userRepository, times(1)).save(any(User.class));
        }
    }

    @Nested
    class DeleteAccountByIdTests {

        @Test
        void deleteAccountById_Success() {
            // Arrange
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

            // Act
            Map<String, String> response = userService.deleteAccount(1L);

            // Assert
            assertNotNull(response);
            assertTrue(response.get("message").contains("account has been deleted successfully."));

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(1)).save(userCaptor.capture());

            User deletedUser = userCaptor.getValue();
            assertEquals("Deleted", deletedUser.getFirstName());
            assertEquals("User", deletedUser.getLastName());
            assertEquals("+0777777777", deletedUser.getPhoneNumber());
            assertNotNull(deletedUser.getDeletedAt());
            assertTrue(deletedUser.getUsername().startsWith("john_doe-deleted"));
            assertTrue(deletedUser.getEmail().startsWith("deleted-"));
        }

        @Test
        void deleteAccountById_ThrowsNotFoundException_WhenUserDoesNotExist() {
            // Arrange
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () -> userService.deleteAccount(1L));
        }

        @Test
        void deleteAccountById_ThrowsBadRequestException_WhenUserIsAlreadyDeleted() {
            // Arrange
            sampleUser.setDeletedAt(LocalDateTime.now());
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

            // Act & Assert
            assertThrows(BadRequestException.class, () -> userService.deleteAccount(1L));
        }

        @Test
        void deleteAccountById_ThrowsBadRequestException_WhenTargetIsAnOwner() {
            // Arrange
            sampleUser.setRole(Role.OWNER);
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

            // Act & Assert
            assertThrows(BadRequestException.class, () -> userService.deleteAccount(1L));
        }
    }

    @Nested
    class UpdateProfileTests {

        @Test
        void updateProfile_ThrowsBadRequestException_WhenUserNotFound() {
            // Arrange
            UpdateProfileDTO dto = new UpdateProfileDTO("New", "Name", "new@example.com", "+987654321");
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(BadRequestException.class, () -> userService.updateProfile(1L, dto));
        }

        @Test
        void updateProfile_Success_WithAllFieldsChangedAndEmailAvailable() {
            // Arrange
            UpdateProfileDTO dto = new UpdateProfileDTO("Jane", "Smith", "jane@example.com", "+987654321");
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.empty());

            // Act
            ProfileDTO result = userService.updateProfile(1L, dto);

            // Assert
            assertNotNull(result);
            assertEquals("Jane", result.firstName());
            assertEquals("Smith", result.lastName());
            assertEquals("jane@example.com", result.email());
            assertEquals("+987654321", result.phoneNumber());
            verify(userRepository, times(1)).save(sampleUser);
        }

        @Test
        void updateProfile_ThrowsAlreadyExistsException_WhenNewEmailIsTaken() {
            // Arrange
            UpdateProfileDTO dto = new UpdateProfileDTO("Jane", "Smith", "taken@example.com", "+987654321");
            User existingUser = User.builder().id(2L).email("taken@example.com").build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.findByEmailIgnoreCase("taken@example.com")).thenReturn(Optional.of(existingUser));

            // Act & Assert
            assertThrows(AlreadyExistsException.class, () -> userService.updateProfile(1L, dto));
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void updateProfile_Success_WhenFieldsAreNullOrEmailIsUnchanged() {
            // Arrange
            // Testing the branches where input fields are null, or email matches old email (case-insensitive)
            UpdateProfileDTO dto = new UpdateProfileDTO(null, null, "JOHN@example.com", null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

            // Act
            ProfileDTO result = userService.updateProfile(1L, dto);

            // Assert
            assertNotNull(result);
            assertEquals("John", result.firstName()); // Unchanged
            assertEquals("john@example.com", result.email()); // Unchanged case
            verify(userRepository, never()).findByEmailIgnoreCase(anyString());
            verify(userRepository, times(1)).save(sampleUser);
        }
    }

    @Nested
    class PromoteUserTests {

        @Test
        void promoteUser_Success() {
            // Arrange
            PromoteDTO dto = new PromoteDTO(1L, Role.OWNER);
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

            // Act
            ProfileDTO result = userService.promoteUser(dto);

            // Assert
            assertNotNull(result);
            assertEquals(Role.OWNER, result.role());
            verify(userRepository, times(1)).save(sampleUser);
        }

        @Test
        void promoteUser_ThrowsBadRequestException_WhenUserDoesNotExist() {
            // Arrange
            PromoteDTO dto = new PromoteDTO(1L, Role.OWNER);
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(BadRequestException.class, () -> userService.promoteUser(dto));
            verify(userRepository, never()).save(any(User.class));
        }


        @Test
        void updateProfile_Success_WhenEmailIsNull() {
            // Arrange
            UpdateProfileDTO dto = new UpdateProfileDTO("Jane", null, null, null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

            // Act
            ProfileDTO result = userService.updateProfile(1L, dto);

            // Assert
            assertEquals("Jane", result.firstName());
            assertEquals("john@example.com", result.email()); // Remains unchanged
            verify(userRepository, times(1)).save(sampleUser);
        }
    }
}