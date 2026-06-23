package com.awbd.cinema.security;

import com.awbd.cinema.services.TicketSetupService.TicketSetupService;
import com.awbd.cinema.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalCatalogSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private TicketSetupService ticketSetupService;

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ticketSetup_ShouldReturn401_WhenNoServiceToken() throws Exception {
        mockMvc.perform(get("/internal/ticket-setup")
                        .param("seatId", "1").param("roomId", "1").param("sessionId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ticketSetup_ShouldReturn200_WhenServiceTokenIsValid() throws Exception {
        String token = jwtUtil.generateServiceToken("booking-service");

        mockMvc.perform(get("/internal/ticket-setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("seatId", "1").param("roomId", "1").param("sessionId", "1"))
                .andExpect(status().isOk());
    }
}
