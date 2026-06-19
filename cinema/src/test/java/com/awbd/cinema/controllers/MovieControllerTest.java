package com.awbd.cinema.controllers;

import com.awbd.cinema.DTOs.MovieDTOs.AdminMovieDTO;
import com.awbd.cinema.DTOs.MovieDTOs.MovieDTO;
import com.awbd.cinema.DTOs.MovieDTOs.SaveMovieDTO;
import com.awbd.cinema.security.SecurityConfig;
import com.awbd.cinema.services.MovieService.MovieService;
import com.awbd.cinema.utils.RestPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MovieController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class MovieControllerTest extends BaseControllerTest{

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private MovieService movieService;


    // --- ADMIN MOVIE LIST ENDPOINT TESTS ---
    @Nested
    @DisplayName("GET /movies/admin/list")
    class GetAdminMovieListTests {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("Should return 200 and page data when user has STAFF role")
        void shouldReturnAdminMovieListWhenAuthorized() throws Exception {
            AdminMovieDTO dto = new AdminMovieDTO(1, "Inception", "url", LocalDateTime.now(), List.of(1), 8.8, "Desc");
            RestPage<AdminMovieDTO> pageResult = new RestPage<>(new PageImpl<>(List.of(dto)));

            when(movieService.getAdminMovieList(1)).thenReturn(pageResult);

            mockMvc.perform(get("/movies/admin/list")
                            .param("page", "1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("Inception"));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Should return 403 Forbidden when user lacks STAFF role")
        void shouldReturnForbiddenWhenNotStaff() throws Exception {
            mockMvc.perform(get("/movies/admin/list")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    // --- SAVE MOVIE ENDPOINT TESTS ---
    @Nested
    @DisplayName("POST /movies/admin/save/{tmdbId}")
    class SaveMovieTests {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("Should return 200 and saved object when authorized")
        void shouldSaveMovieWhenAuthorized() throws Exception {
            SaveMovieDTO responseDto = new SaveMovieDTO("Avatar", LocalDateTime.now(), "Epic film", 7.9, 162, "12+", List.of(), "https://example.com/poster.jpg");
            when(movieService.saveMovie(123)).thenReturn(responseDto);

            mockMvc.perform(post("/movies/admin/save/123")
                            .with(csrf()) // Required if CSRF protection is active
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Avatar"))
                    .andExpect(jsonPath("$.duration").value(162));
        }

        @Test
        @WithAnonymousUser
        @DisplayName("Should return 401 Unauthorized when no user is authenticated")
        void shouldReturnUnauthorizedWhenAnonymous() throws Exception {
            mockMvc.perform(post("/movies/admin/save/123")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }

    // --- GET USER MOVIE LIST ENDPOINT TESTS (PUBLIC) ---
    @Nested
    @DisplayName("GET /movies")
    class GetMoviesTests {

        @Test
        @DisplayName("Should return 200 with public movie data and apply default parameter values")
        void shouldReturnPublicMovieList() throws Exception {
            MovieDTO dto = new MovieDTO(100L, "Interstellar", LocalDateTime.now(), "Space travel", 8.6, 169, "12+", List.of(), "https://example.com/poster.jpg");
            RestPage<MovieDTO> pageResult = new RestPage<>(new PageImpl<>(List.of(dto)));

            when(movieService.getUserMovieList(1, 10, null, null, null, null, null, null, null))
                    .thenReturn(pageResult);

            mockMvc.perform(get("/movies")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(100))
                    .andExpect(jsonPath("$.content[0].title").value("Interstellar"));
        }

        @Test
        @DisplayName("Should forward explicitly provided filter query parameters to service layer")
        void shouldForwardQueryParameters() throws Exception {
            RestPage<MovieDTO> emptyPage = new RestPage<>(new PageImpl<>(List.of()));

            when(movieService.getUserMovieList(2, 5, "The Matrix", 8.0, 9.0, "16+", "1999-01-01", "2000-01-01", "ACTION"))
                    .thenReturn(emptyPage);

            mockMvc.perform(get("/movies")
                            .param("page", "2")
                            .param("size", "5")
                            .param("title", "The Matrix")
                            .param("minRating", "8.0")
                            .param("maxRating", "9.0")
                            .param("ageRating", "16+")
                            .param("releaseFrom", "1999-01-01")
                            .param("releaseTo", "2000-01-01")
                            .param("genre", "ACTION")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(movieService).getUserMovieList(2, 5, "The Matrix", 8.0, 9.0, "16+", "1999-01-01", "2000-01-01", "ACTION");
        }
    }

    // --- GET SINGLE MOVIE ENDPOINT TESTS (PUBLIC) ---
    @Nested
    @DisplayName("GET /movies/{id}")
    class GetMovieTests {

        @Test
        @DisplayName("Should allow access to unauthenticated user and return movie payload")
        void shouldReturnMovieById() throws Exception {
            MovieDTO dto = new MovieDTO(45L, "Gladiator", LocalDateTime.now(), "Roman general", 8.5, 155, "16+", List.of(),"https://example.com/poster.jpg");
            when(movieService.getMovie(45L)).thenReturn(dto);

            mockMvc.perform(get("/movies/45")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(45))
                    .andExpect(jsonPath("$.title").value("Gladiator"));
        }
    }

    // --- UPDATE MOVIE ENDPOINT TESTS ---
    @Nested
    @DisplayName("PUT /movies/{id}")
    class UpdateMovieTests {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("Should return 200 and updated data object when caller has STAFF role")
        void shouldUpdateMovieWhenAuthorized() throws Exception {
            SaveMovieDTO requestBody = new SaveMovieDTO("Memento", LocalDateTime.now(), "Updated Desc", 8.4, 113, "16+", List.of(),"https://example.com/poster.jpg");
            MovieDTO responseDto = new MovieDTO(1L, "Memento", LocalDateTime.now(), "Updated Desc", 8.4, 113, "16+", List.of(),"https://example.com/poster.jpg");

            when(movieService.updateMovie(eq(1L), any(SaveMovieDTO.class))).thenReturn(responseDto);

            mockMvc.perform(put("/movies/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Memento"))
                    .andExpect(jsonPath("$.description").value("Updated Desc"));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Should deny mutation and return 403 when user lacks STAFF permission tier")
        void shouldDenyUpdateForStandardUser() throws Exception {
            SaveMovieDTO requestBody = new SaveMovieDTO("Memento", LocalDateTime.now(), "Desc", 8.4, 113, "16+", List.of(),"https://example.com/poster.jpg");

            mockMvc.perform(put("/movies/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isForbidden());
        }
    }

    // --- DELETE MOVIE ENDPOINT TESTS ---
    @Nested
    @DisplayName("DELETE /movies/{id}")
    class DeleteMovieTests {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("Should return 204 No Content status when deletion request completes successfully")
        void shouldDeleteMovieWhenAuthorized() throws Exception {
            doNothing().when(movieService).deleteMovie(99L);

            mockMvc.perform(delete("/movies/99")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(movieService).deleteMovie(99L);
        }

        @Test
        @WithAnonymousUser
        @DisplayName("Should reject execution routing with 401 Unauthorized for anonymous callers")
        void shouldDenyDeleteWhenAnonymous() throws Exception {
            mockMvc.perform(delete("/movies/99")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }
}