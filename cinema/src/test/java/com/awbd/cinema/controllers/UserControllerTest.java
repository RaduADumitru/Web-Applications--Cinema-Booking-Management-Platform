package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.UserDTOs.ProfileDTO;
import com.awbd.cinema.DTOs.UserDTOs.PromoteDTO;
import com.awbd.cinema.DTOs.UserDTOs.UpdateProfileDTO;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.security.CustomUserDetails;
import com.awbd.cinema.security.CustomUserDetailsService;
import com.awbd.cinema.services.UserService.UserService;
import com.awbd.cinema.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserService userService;

    @MockitoBean
    protected JwtUtil jwtUtil;

    @MockitoBean
    protected CustomUserDetailsService customUserDetailsService;

    private CustomUserDetails mockUserDetails;
    private ProfileDTO sampleProfileDTO;

    @BeforeEach
    void setUp() {
        mockUserDetails = mock(CustomUserDetails.class);
        when(mockUserDetails.getId()).thenReturn(1L);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                mockUserDetails, null, mockUserDetails.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        sampleProfileDTO = new ProfileDTO(
                "john_doe", "John", "Doe",
                "john.doe@example.com", "+1234567890", 150, Role.USER
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class GetUserEndpointTests {

        @Test
        void getUser_ReturnsAuthenticatedUserProfile() throws Exception {
            // Arrange
            when(userService.getProfile(1L)).thenReturn(sampleProfileDTO);

            // Act & Assert
            mockMvc.perform(get("/user"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("john_doe"))
                    .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                    .andExpect(jsonPath("$.loyaltyPoints").value(150))
                    .andExpect(jsonPath("$.role").value("USER"));

            verify(userService, times(1)).getProfile(1L);
        }
    }

    @Nested
    class DeleteUserEndpointTests {

        @Test
        void deleteUser_SelfAccount_ReturnsSuccessMessage() throws Exception {
            // Arrange
            Map<String, String> successResponse = Map.of("message", "Your account has been deleted successfully.");
            when(userService.deleteAccount(any(CustomUserDetails.class))).thenReturn(successResponse);

            // Act & Assert
            mockMvc.perform(delete("/user"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Your account has been deleted successfully."));

            verify(userService, times(1)).deleteAccount(any(CustomUserDetails.class));
        }

        @Test
        void deleteUser_ByIdWithOWNERPrivileges_ReturnsSuccessMessage() throws Exception {
            // Arrange
            Long targetUserId = 42L;
            Map<String, String> successResponse = Map.of("message", "deleted_user's account has been deleted successfully.");
            when(userService.deleteAccount(targetUserId)).thenReturn(successResponse);

            // Act & Assert
            mockMvc.perform(delete("/user/{id}", targetUserId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("deleted_user's account has been deleted successfully."));

            verify(userService, times(1)).deleteAccount(targetUserId);
        }
    }

    @Nested
    class UpdateProfileEndpointTests {

        @Test
        void updateProfile_WithValidPayload_ReturnsUpdatedProfile() throws Exception {
            // Arrange
            UpdateProfileDTO validUpdateDTO = new UpdateProfileDTO("Jane", "Smith", "jane.smith@example.com", "+9876543210");
            ProfileDTO updatedProfileDTO = new ProfileDTO("john_doe", "Jane", "Smith", "jane.smith@example.com", "+9876543210", 150, Role.USER);

            when(userService.updateProfile(eq(1L), any(UpdateProfileDTO.class))).thenReturn(updatedProfileDTO);

            // Act & Assert
            mockMvc.perform(patch("/user")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Jane"))
                    .andExpect(jsonPath("$.lastName").value("Smith"))
                    .andExpect(jsonPath("$.email").value("jane.smith@example.com"));

            verify(userService, times(1)).updateProfile(eq(1L), any(UpdateProfileDTO.class));
        }

        @Test
        void updateProfile_WithInvalidPayload_ReturnsUnprocessableContent() throws Exception {
            // Arrange
            UpdateProfileDTO invalidUpdateDTO = new UpdateProfileDTO("J", "S", "not-an-email", "123");

            // Act & Assert
            mockMvc.perform(patch("/user")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidUpdateDTO)))
                    .andExpect(status().isUnprocessableContent());

            verify(userService, never()).updateProfile(any(), any());
        }
    }

    @Nested
    class PromoteUserEndpointTests {

        @Test
        void promoteUser_WithValidTarget_ReturnsPromotedProfile() throws Exception {
            // Arrange
            PromoteDTO promoteDTO = new PromoteDTO(2L, Role.OWNER);
            ProfileDTO promotedProfileDTO = new ProfileDTO("target_user", "Alex", "Jones", "alex@example.com", "+1112223333", 0, Role.OWNER);

            when(userService.promoteUser(any(PromoteDTO.class))).thenReturn(promotedProfileDTO);

            // Act & Assert
            mockMvc.perform(patch("/user/promote")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(promoteDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("OWNER"));

            verify(userService, times(1)).promoteUser(any(PromoteDTO.class));
        }

        @Test
        void promoteUser_SelfPromotion_ReturnsBadRequest() throws Exception {
            // Arrange - target ID matches the authenticated mockUserDetails ID (1L)
            PromoteDTO selfPromoteDTO = new PromoteDTO(1L, Role.OWNER);

            // Act & Assert
            mockMvc.perform(patch("/user/promote")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(selfPromoteDTO)))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).promoteUser(any());
        }

        @Test
        void promoteUser_WithInvalidPayload_ReturnsUnprocessableContent() throws Exception {
            // Arrange
            PromoteDTO invalidPromoteDTO = new PromoteDTO(-5L, Role.OWNER);

            // Act & Assert
            mockMvc.perform(patch("/user/promote")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidPromoteDTO)))
                    .andExpect(status().isUnprocessableContent());

            verify(userService, never()).promoteUser(any());
        }
    }
}