package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.ScreenSessionDTOs.SaveScreenSessionDTO;
import com.awbd.cinema.DTOs.ScreenSessionDTOs.ScreenSessionDTO;
import com.awbd.cinema.enums.Format;
import com.awbd.cinema.enums.Role;
import com.awbd.cinema.services.ScreenSessionService.ScreenSessionService;
import com.awbd.cinema.utils.RestPage;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScreenSessionController.class)
class ScreenSessionControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ScreenSessionService screenSessionService;

    private ScreenSessionDTO createSampleSessionDTO() {
        return new ScreenSessionDTO(
                1L,
                LocalDate.of(2026, 7, 1),
                LocalTime.of(18, 0),
                LocalTime.of(20, 30),
                500L,
                "Inception",
                30L,
                Format.THREE_D,
                15,
                java.util.List.of(2L)
        );
    }

    // ==========================================
    // POST /screen-sessions (Staff Access Required)
    // ==========================================
    @Nested
    @DisplayName("POST /screen-sessions")
    class CreateScreenSession {

        @Test
        @DisplayName("Should return 201 Created when executed by STAFF with valid payload")
        void createScreenSession_StaffAndValidPayload_ReturnsCreated() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            SaveScreenSessionDTO dto = new SaveScreenSessionDTO(
                    500L, LocalDate.of(2026, 7, 1),
                    LocalTime.of(18, 0), LocalTime.of(20, 30),
                    30L, 10L
            );
            ScreenSessionDTO responseDto = createSampleSessionDTO();

            when(screenSessionService.createScreenSession(any(SaveScreenSessionDTO.class))).thenReturn(responseDto);

            mockMvc.perform(post("/screen-sessions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.movieTitle").value("Inception"))
                    .andExpect(jsonPath("$.format").value("THREE_D"));
        }

        @Test
        @DisplayName("Should return 422 Unprocessable Content when validation fails (missing mandatory field)")
        void createScreenSession_MissingMovieId_ReturnsUnprocessableContent() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            // Movie ID is marked @NotNull
            SaveScreenSessionDTO dto = new SaveScreenSessionDTO(
                    null, LocalDate.of(2026, 7, 1),
                    LocalTime.of(18, 0), LocalTime.of(20, 30),
                    30L, 10L
            );

            mockMvc.perform(post("/screen-sessions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isUnprocessableContent());
        }

        @Test
        @DisplayName("Should return 403 Forbidden when executed by standard USER")
        void createScreenSession_RegularUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser();
            SaveScreenSessionDTO dto = new SaveScreenSessionDTO(
                    500L, LocalDate.of(2026, 7, 1),
                    LocalTime.of(18, 0), LocalTime.of(20, 30),
                    30L, 10L
            );

            mockMvc.perform(post("/screen-sessions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }
    }

    // ==========================================
    // GET Read-only public collections endpoints
    // ==========================================
    @Nested
    @DisplayName("GET read collections queries")
    class ReadScreenSessions {

        @Test
        @DisplayName("GET /screen-sessions should return matching paginated elements specification format")
        void getScreenSessions_WithFilters_ReturnsPaginatedResult() throws Exception {
            loginAsDefaultUser();
            ScreenSessionDTO sessionDto = createSampleSessionDTO();
            when(screenSessionService.getScreenSessions(eq(500L), eq("THREE_D"), any(Pageable.class)))
                    .thenReturn(new RestPage<>(new PageImpl<>(List.of(sessionDto))));

            mockMvc.perform(get("/screen-sessions")
                            .param("movieId", "500")
                            .param("format", "THREE_D")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(1L))
                    .andExpect(jsonPath("$.content[0].movieTitle").value("Inception"));
        }

        @Test
        @DisplayName("GET /screen-sessions/movie/{movieId} should return target session listings")
        void getScreenSessionsByMovie_ReturnsPaginatedResult() throws Exception {
            loginAsDefaultUser();
            ScreenSessionDTO sessionDto = createSampleSessionDTO();
            when(screenSessionService.getScreenSessionsByMovie(eq(500L), any(Pageable.class)))
                    .thenReturn(new RestPage<>(new PageImpl<>(List.of(sessionDto))));

            mockMvc.perform(get("/screen-sessions/movie/500"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].movieId").value(500L));
        }

        @Test
        @DisplayName("GET /screen-sessions/{id} should return accurate record details matching key")
        void getScreenSession_ValidId_ReturnsOk() throws Exception {
            loginAsDefaultUser();
            ScreenSessionDTO sessionDto = createSampleSessionDTO();
            when(screenSessionService.getScreenSession(1L)).thenReturn(sessionDto);

            mockMvc.perform(get("/screen-sessions/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.points").value(15));
        }
    }

    // ==========================================
    // PUT /screen-sessions/{id} (Staff Access Required)
    // ==========================================
    @Nested
    @DisplayName("PUT /screen-sessions/{id}")
    class UpdateScreenSession {

        @Test
        @DisplayName("Should return 200 OK and serialize updated state payload when run by STAFF")
        void updateScreenSession_StaffUser_ReturnsOk() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            SaveScreenSessionDTO updateDto = new SaveScreenSessionDTO(
                    500L, LocalDate.of(2026, 7, 1),
                    LocalTime.of(19, 0), LocalTime.of(21, 30),
                    30L, 10L
            );
            ScreenSessionDTO responseDto = createSampleSessionDTO();

            when(screenSessionService.updateScreenSession(eq(1L), any(SaveScreenSessionDTO.class)))
                    .thenReturn(responseDto);

            mockMvc.perform(put("/screen-sessions/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1L));
        }

        @Test
        @DisplayName("Should return 403 Forbidden when run by regular USER profile")
        void updateScreenSession_RegularUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser();
            SaveScreenSessionDTO updateDto = new SaveScreenSessionDTO(
                    500L, LocalDate.of(2026, 7, 1),
                    LocalTime.of(19, 0), LocalTime.of(21, 30),
                    30L, 10L
            );

            mockMvc.perform(put("/screen-sessions/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isForbidden());
        }
    }

    // ==========================================
    // DELETE /screen-sessions/{id} (Staff Access Required)
    // ==========================================
    @Nested
    @DisplayName("DELETE /screen-sessions/{id}")
    class DeleteScreenSession {

        @Test
        @DisplayName("Should return 24 No Content when executing request as STAFF")
        void deleteScreenSession_StaffUser_ReturnsNoContent() throws Exception {
            loginAs(2L, "staff_user", Role.STAFF);
            doNothing().when(screenSessionService).deleteScreenSession(1L);

            mockMvc.perform(delete("/screen-sessions/1")
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should return 403 Forbidden when executing request as standard USER")
        void deleteScreenSession_RegularUser_ReturnsForbidden() throws Exception {
            loginAsDefaultUser();

            mockMvc.perform(delete("/screen-sessions/1")
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }
}