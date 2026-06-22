package com.awbd.cinema;

import com.awbd.cinema.DTOs.AuthDTOs.LoginDTO;
import com.awbd.cinema.DTOs.AuthDTOs.RegisterDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("End-to-End: User Registration and Login Flow")
    void testUserRegistrationAndLogin() throws Exception {
        // 1. Register a new user
        RegisterDTO registerDTO = new RegisterDTO(
                "testuser_e2e", "Password123!", "Password123!",
                "test_e2e@example.com", "John", "Doe", "+1234567890"
        );

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Account created successfully."))
                .andExpect(jsonPath("$.username").value("testuser_e2e"));

        // 2. Attempt to register the same user again (should fail)
        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDTO)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username or email address is already in use."));

        // 3. Log in with the newly registered user
        LoginDTO loginDTO = new LoginDTO("testuser_e2e", "Password123!");

        var loginResult = mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser_e2e"))
                .andExpect(cookie().exists("jwt"))
                .andExpect(cookie().exists("refresh"))
                .andReturn();

        // 4. Refresh the token using the refresh cookie from login
        jakarta.servlet.http.Cookie refreshCookie = loginResult.getResponse().getCookie("refresh");

        mockMvc.perform(post("/auth/refresh")
                        .with(csrf())
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("jwt"))
                .andExpect(cookie().exists("refresh"))
                .andExpect(jsonPath("$.message").value("Token refreshed successfully."));

        // 5. Logout — cookies should be cleared
        mockMvc.perform(post("/auth/logout")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(cookie().value("jwt", ""))
                .andExpect(cookie().maxAge("jwt", 0))
                .andExpect(cookie().value("refresh", ""))
                .andExpect(cookie().maxAge("refresh", 0))
                .andExpect(jsonPath("$.message").value("Logged out successfully."));

        // 6. Attempt to log in with incorrect password (should fail)
        LoginDTO incorrectLoginDTO = new LoginDTO("testuser_e2e", "WrongPassword!");

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(incorrectLoginDTO)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Invalid account details."));
    }
}

