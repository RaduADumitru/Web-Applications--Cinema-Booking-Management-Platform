package com.awbd.cinema;

import com.awbd.cinema.DTOs.MovieDTOs.SaveMovieDTO;
import com.awbd.cinema.entities.Genre;
import com.awbd.cinema.enums.GenreType;
import com.awbd.cinema.repositories.GenreRepository;
import info.movito.themoviedbapi.TmdbApi;
import info.movito.themoviedbapi.TmdbMovies;
import info.movito.themoviedbapi.model.movies.MovieDb;
import info.movito.themoviedbapi.tools.TmdbException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class MovieIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GenreRepository genreRepository;

    @MockitoBean
    private TmdbApi tmdbApi;

    @MockitoBean
    private TmdbMovies tmdbMovies;

    private Genre actionGenre;
    private final int TMDB_MOVIE_ID = 12345;

    @BeforeEach
    void setUp() throws TmdbException {
        // Ensure Action genre exists in DB for movie creation
        actionGenre = genreRepository.findByType(GenreType.ACTION)
                .orElseGet(() -> genreRepository.save(Genre.builder().type(GenreType.ACTION).build()));

        // Mock TMDB API responses
        when(tmdbApi.getMovies()).thenReturn(tmdbMovies);

        MovieDb mockMovieDb = new MovieDb();
        mockMovieDb.setId(TMDB_MOVIE_ID);
        mockMovieDb.setTitle("Mock Movie Title");
        mockMovieDb.setOverview("Mock Movie Overview");
        mockMovieDb.setReleaseDate("2023-01-01");
        mockMovieDb.setVoteAverage(7.5);
        mockMovieDb.setRuntime(120);
        mockMovieDb.setAdult(false); // For age rating logic

        info.movito.themoviedbapi.model.core.Genre tmdbGenre = new info.movito.themoviedbapi.model.core.Genre();
        tmdbGenre.setId(28); // TMDB ID for Action
        tmdbGenre.setName("Action");
        mockMovieDb.setGenres(Collections.singletonList(tmdbGenre));

        when(tmdbMovies.getDetails(anyInt(), anyString())).thenReturn(mockMovieDb);
    }

    @Test
    @DisplayName("End-to-End: Create, Retrieve, Update, and Delete a Movie")
    void testMovieCrudFlow() throws Exception {
        // 1. Create a Movie (via POST /movies/admin/save/{tmdbId})
        // This requires a STAFF user to be authorized.
        mockMvc.perform(post("/movies/admin/save/{tmdbId}", TMDB_MOVIE_ID)
                        .with(user("staff").roles("STAFF"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Mock Movie Title"))
                .andExpect(jsonPath("$.description").value("Mock Movie Overview"));

        // 2. Retrieve the created Movie (via GET /movies)

        // Assuming the ID is the TMDB_MOVIE_ID for simplicity.
        Long createdMovieId = (long) TMDB_MOVIE_ID;

        mockMvc.perform(get("/movies/{id}", createdMovieId)
                        .with(user("staff").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Mock Movie Title"))
                .andExpect(jsonPath("$.description").value("Mock Movie Overview"));

        // 3. Update the Movie (via PUT /movies/{id})
        SaveMovieDTO updateDto = new SaveMovieDTO(
                "Updated Mock Movie",
                LocalDateTime.now(),
                "An updated mock description.",
                8.0,
                130,
                "PG-13",
                List.of(GenreType.ACTION),
                "https://example.com/poster.jpg"
        );

        mockMvc.perform(put("/movies/{id}", createdMovieId)
                        .with(user("staff").roles("STAFF"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Mock Movie"))
                .andExpect(jsonPath("$.ageRating").value("PG-13"));

        // Verify update by retrieving again
        mockMvc.perform(get("/movies/{id}", createdMovieId)
                        .with(user("staff").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Mock Movie"))
                .andExpect(jsonPath("$.ageRating").value("PG-13"));

        // 4. Delete the Movie (via DELETE /movies/{id})
        mockMvc.perform(delete("/movies/{id}", createdMovieId)
                        .with(user("staff").roles("STAFF"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify deletion (should return 404 Not Found)
        mockMvc.perform(get("/movies/{id}", createdMovieId)
                        .with(user("staff").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
