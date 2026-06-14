package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.DTOs.NotificationDTOs.NotificationDTO;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.security.CustomUserDetails;
import com.awbd.cinema.services.NotificationService.NotificationService;
import com.awbd.cinema.utils.RestPage;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private NotificationService notificationService;

    @Nested
    @DisplayName("POST /notifications")
    class CreateNotificationTests {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("Should return 201 Created when request is valid and user has STAFF role")
        void createNotification_ValidRequest_ReturnsCreated() throws Exception {
            // Arrange
            CreateNotificationDTO requestDto = new CreateNotificationDTO(
                    NotificationType.TICKET_BOUGHT, "Your confirmation code is 1234", 1L
            );
            NotificationDTO responseDto = new NotificationDTO(
                    10L, NotificationType.TICKET_BOUGHT, "Your confirmation code is 1234",
                    LocalDateTime.now(), null, 1L, null
            );

            when(notificationService.createNotification(any(CreateNotificationDTO.class)))
                    .thenReturn(responseDto);

            // Act & Assert
            mockMvc.perform(post("/notifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10L))
                    .andExpect(jsonPath("$.content").value("Your confirmation code is 1234"))
                    .andExpect(jsonPath("$.userId").value(1L));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Should return 403 Forbidden when user does not have STAFF role")
        void createNotification_InvalidRole_ReturnsForbidden() throws Exception {
            CreateNotificationDTO requestDto = new CreateNotificationDTO(
                    NotificationType.TICKET_BOUGHT, "Content", 1L
            );

            mockMvc.perform(post("/notifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("Should return 400 Bad Request when validation constraints fail")
        void createNotification_InvalidBody_ReturnsBadRequest() throws Exception {
            // Invalid DTO: empty content and null type
            CreateNotificationDTO requestDto = new CreateNotificationDTO(null, "", 1L);

            mockMvc.perform(post("/notifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto)))
                    .andExpect(status().isUnprocessableContent());
        }
    }

    @Nested
    @DisplayName("GET /notifications/my")
    class GetMyNotificationsTests {

        @Test
        @DisplayName("Should return 200 OK with paginated notifications matching the authenticated user")
        void getMyNotifications_AuthenticatedUser_ReturnsPagedData() throws Exception {
            Long mockUserId = 42L;

            CustomUserDetails mockUserDetails = new CustomUserDetails(mockUserId, "test_user", "password123", Role.USER, null);

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    mockUserDetails, null, mockUserDetails.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 3. Set up your mock service responses
            NotificationDTO notification = new NotificationDTO(
                    1L, NotificationType.MOVIE_REMINDER, "The movie starts soon!",
                    LocalDateTime.now(), null, mockUserId, null
            );
            RestPage<NotificationDTO> pageResponse = new RestPage<>(new PageImpl<>(List.of(notification), PageRequest.of(0, 20), 1));

            when(notificationService.getMyNotifications(eq(mockUserId), any(Pageable.class)))
                    .thenReturn(pageResponse);

            try {
                mockMvc.perform(get("/notifications/my")
                                .param("page", "0")
                                .param("size", "20"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content[0].id").value(1L))
                        .andExpect(jsonPath("$.content[0].content").value("The movie starts soon!"))
                        .andExpect(jsonPath("$.page.totalElements").value(1));
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    @Nested
    @DisplayName("GET /notifications/{id}")
    class GetNotificationTests {

        @Test
        @WithMockUser
        @DisplayName("Should return 200 OK and notification data when resource exists")
        void getNotification_ValidId_ReturnsNotification() throws Exception {
            // Arrange
            Long notificationId = 100L;
            NotificationDTO responseDto = new NotificationDTO(
                    notificationId, NotificationType.SUCCESSFUL_PAYMENT, "Payment complete",
                    LocalDateTime.now(), null, 1L, null
            );

            when(notificationService.getNotification(notificationId)).thenReturn(responseDto);

            // Act & Assert
            mockMvc.perform(get("/notifications/{id}", notificationId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(notificationId))
                    .andExpect(jsonPath("$.content").value("Payment complete"));
        }
    }

    @Nested
    @DisplayName("PATCH /notifications/{id}/send")
    class MarkAsSentTests {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("Should return 200 OK when marked as sent by a STAFF user")
        void markAsSent_ValidStaffUser_ReturnsUpdatedNotification() throws Exception {
            // Arrange
            Long notificationId = 5L;
            NotificationDTO responseDto = new NotificationDTO(
                    notificationId, NotificationType.EMAIL_VERIFICATION, "Verify email",
                    LocalDateTime.now(), LocalDateTime.now(), 1L, null
            );

            when(notificationService.markAsSent(notificationId)).thenReturn(responseDto);

            // Act & Assert
            mockMvc.perform(patch("/notifications/{id}/send", notificationId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(notificationId))
                    .andExpect(jsonPath("$.sentDate").exists());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Should return 403 Forbidden when marked as sent by a non-STAFF user")
        void markAsSent_RegularUser_ReturnsForbidden() throws Exception {
            mockMvc.perform(patch("/notifications/{id}/send", 5L))
                    .andExpect(status().isForbidden());
        }
    }
}