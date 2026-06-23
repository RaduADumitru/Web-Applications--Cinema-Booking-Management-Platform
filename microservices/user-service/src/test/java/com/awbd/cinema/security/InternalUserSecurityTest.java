package com.awbd.cinema.security;

import com.awbd.cinema.services.UserService.UserService;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalUserSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private UserService userService;

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getLoyaltyPoints_ShouldReturn401_WhenNoServiceToken() throws Exception {
        mockMvc.perform(get("/internal/users/1/loyalty-points"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getLoyaltyPoints_ShouldReturn200_WhenServiceTokenIsValid() throws Exception {
        String token = jwtUtil.generateServiceToken("booking-service");

        mockMvc.perform(get("/internal/users/1/loyalty-points")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    // Reproduction of the CI 403: a stale non-SERVICE authentication is present in the
    // context (in CI this leaks from another test's ordering); a valid service token must
    // still authenticate as ROLE_SERVICE and return 200.
    @Test
    void getLoyaltyPoints_ShouldReturn200_WhenServiceTokenIsValid_EvenWithPreexistingAuth() throws Exception {
        String token = jwtUtil.generateServiceToken("booking-service");

        mockMvc.perform(get("/internal/users/1/loyalty-points")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .with(user("stale_user").roles("USER")))
                .andExpect(status().isOk());
    }
}
