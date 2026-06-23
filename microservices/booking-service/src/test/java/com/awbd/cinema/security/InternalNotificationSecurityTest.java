package com.awbd.cinema.security;

import com.awbd.cinema.DTOs.NotificationDTOs.CreateNotificationDTO;
import com.awbd.cinema.enums.NotificationType;
import com.awbd.cinema.services.NotificationService.NotificationService;
import com.awbd.cinema.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalNotificationSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private NotificationService notificationService;

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createNotification_ShouldReturn401_WhenNoServiceToken() throws Exception {
        CreateNotificationDTO dto =
                new CreateNotificationDTO(NotificationType.EMAIL_VERIFICATION, "Welcome!", 1L);

        mockMvc.perform(post("/internal/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createNotification_ShouldReturn201_WhenServiceTokenIsValid() throws Exception {
        CreateNotificationDTO dto =
                new CreateNotificationDTO(NotificationType.EMAIL_VERIFICATION, "Welcome!", 1L);
        String token = jwtUtil.generateServiceToken("user-service");

        mockMvc.perform(post("/internal/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }
}
