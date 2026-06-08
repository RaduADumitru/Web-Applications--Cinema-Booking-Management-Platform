package com.awbd.cinema.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.awbd.cinema.DTOs.MovieDTOs.AdminMovieDTO;
import com.awbd.cinema.DTOs.MovieDTOs.MovieDTO;
import com.awbd.cinema.DTOs.MovieDTOs.SaveMovieDTO;
import com.awbd.cinema.entities.Genre;
import com.awbd.cinema.entities.Movie;
import com.awbd.cinema.enums.GenreType;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.GenreRepository;
import com.awbd.cinema.repositories.MovieRepository;
import com.awbd.cinema.services.MovieService.MovieServiceImpl;
import info.movito.themoviedbapi.tools.TmdbException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

// Assuming these are your custom exceptions and models
import info.movito.themoviedbapi.TmdbApi;
import info.movito.themoviedbapi.TmdbMovies;
import info.movito.themoviedbapi.TmdbMovieLists;
import info.movito.themoviedbapi.model.core.MovieResultsPage;
import info.movito.themoviedbapi.model.movies.MovieDb;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock private TmdbApi tmdbApi;
    @Mock private MovieRepository movieRepository;
    @Mock private GenreRepository genreRepository;

    @Mock private TmdbMovieLists tmdbMovieLists;
    @Mock private TmdbMovies tmdbMovies;

    @InjectMocks
    private MovieServiceImpl movieService;

    private Movie sampleMovie;
    private Genre sampleGenre;

    @BeforeEach
    void setUp() {
        sampleGenre = Genre.builder().id(1L).type(GenreType.ACTION).build();

        sampleMovie = Movie.builder()
                .id(100L)
                .title("Inception")
                .releaseDate(LocalDateTime.of(2010, 7, 16, 0, 0))
                .description("A thief who steals corporate secrets...")
                .rating(8.8)
                .duration(148)
                .ageRating("12+")
                .genres(new ArrayList<>(List.of(sampleGenre)))
                .build();
    }

    @Nested
    @DisplayName("Tests for getAdminMovieList")
    class GetAdminMovieListTests {

        @Test
        @DisplayName("Should return a paginated list of AdminMovieDTOs successfully")
        void shouldReturnAdminMovieList() throws Exception {
            // Arrange
            int page = 1;
            MovieResultsPage mockResultsPage = mock(MovieResultsPage.class);
            info.movito.themoviedbapi.model.core.Movie tmdbMovie = mock(info.movito.themoviedbapi.model.core.Movie.class);

            when(tmdbMovie.getId()).thenReturn(100);
            when(tmdbMovie.getTitle()).thenReturn("TMDB Movie");
            when(tmdbMovie.getPosterPath()).thenReturn("/path.jpg");
            when(tmdbMovie.getReleaseDate()).thenReturn("2026-01-01");

            when(tmdbApi.getMovieLists()).thenReturn(tmdbMovieLists);
            when(tmdbMovieLists.getPopular("en-US", page, "GBR")).thenReturn(mockResultsPage);
            when(mockResultsPage.getResults()).thenReturn(List.of(tmdbMovie));
            when(mockResultsPage.getTotalResults()).thenReturn(1);

            // Act
            Page<AdminMovieDTO> result = movieService.getAdminMovieList(page);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().title()).isEqualTo("TMDB Movie");
            assertThat(result.getContent().getFirst().imageUrl()).contains("/path.jpg");
        }

        @Test
        @DisplayName("Should default to page 1 when input page is null or less than 1")
        void shouldDefaultToPageOne() throws Exception {
            MovieResultsPage mockResultsPage = mock(MovieResultsPage.class);
            when(tmdbApi.getMovieLists()).thenReturn(tmdbMovieLists);
            when(tmdbMovieLists.getPopular("en-US", 1, "GBR")).thenReturn(mockResultsPage);

            movieService.getAdminMovieList(null);
            movieService.getAdminMovieList(0);

            verify(tmdbMovieLists, times(2)).getPopular("en-US", 1, "GBR");
        }

        @Test
        @DisplayName("Should throw BadRequestException when TMDB Api fails")
        void shouldThrowBadRequestExceptionOnTmdbFailure() throws Exception {
            when(tmdbApi.getMovieLists()).thenReturn(tmdbMovieLists);
            when(tmdbMovieLists.getPopular(anyString(), anyInt(), anyString()))
                    .thenThrow(new TmdbException("API Down"));

            assertThatThrownBy(() -> movieService.getAdminMovieList(1))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Error while fetching movies.");
        }
    }

    @Nested
    @DisplayName("Tests for saveMovie")
    class SaveMovieTests {

        @Test
        @DisplayName("Should save a new movie successfully when it does not exist in the DB")
        void shouldSaveNewMovie() throws Exception {
            // Arrange
            int adminMovieId = 123;
            MovieDb tmdbMovie = mock(MovieDb.class);
            when(tmdbMovie.getId()).thenReturn(adminMovieId);
            when(tmdbMovie.getTitle()).thenReturn("Interstellar");
            when(tmdbMovie.getReleaseDate()).thenReturn("2014-11-07");
            when(tmdbMovie.getAdult()).thenReturn(false);

            info.movito.themoviedbapi.model.core.Genre tmdbGenre = mock(info.movito.themoviedbapi.model.core.Genre.class);
            when(tmdbGenre.getId()).thenReturn(28); // Assuming 28 maps to ACTION
            when(tmdbMovie.getGenres()).thenReturn(List.of(tmdbGenre));

            when(tmdbApi.getMovies()).thenReturn(tmdbMovies);
            when(tmdbMovies.getDetails(adminMovieId, "en-US")).thenReturn(tmdbMovie);
            when(movieRepository.findByIdIncludingDeleted((long) adminMovieId)).thenReturn(null);
            when(genreRepository.findByTypeIn(any())).thenReturn(List.of(sampleGenre));

            // Act
            SaveMovieDTO result = movieService.saveMovie(adminMovieId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("Interstellar");
            verify(movieRepository, times(1)).save(any(Movie.class));
        }

        @Test
        @DisplayName("Should throw AlreadyExistsException if movie is present and not deleted")
        void shouldThrowExceptionIfMovieExists() throws Exception {
            int adminMovieId = 100;
            MovieDb tmdbMovie = mock(MovieDb.class);
            when(tmdbMovie.getId()).thenReturn(adminMovieId);

            when(tmdbApi.getMovies()).thenReturn(tmdbMovies);
            when(tmdbMovies.getDetails(adminMovieId, "en-US")).thenReturn(tmdbMovie);
            when(movieRepository.findByIdIncludingDeleted(100L)).thenReturn(sampleMovie); // sampleMovie has deletedAt = null

            assertThatThrownBy(() -> movieService.saveMovie(adminMovieId))
                    .isInstanceOf(AlreadyExistsException.class)
                    .hasMessageContaining("Movie already exists.");
        }

        @Test
        @DisplayName("Should restore and update a soft-deleted movie instead of throwing an error")
        void shouldRestoreSoftDeletedMovie() throws Exception {
            int adminMovieId = 100;
            sampleMovie.setDeletedAt(LocalDateTime.now()); // Mark as soft-deleted

            MovieDb tmdbMovie = mock(MovieDb.class);
            when(tmdbMovie.getId()).thenReturn(adminMovieId);

            when(tmdbApi.getMovies()).thenReturn(tmdbMovies);
            when(tmdbMovies.getDetails(adminMovieId, "en-US")).thenReturn(tmdbMovie);
            when(movieRepository.findByIdIncludingDeleted(100L)).thenReturn(sampleMovie);
            when(movieRepository.save(any(Movie.class))).thenReturn(sampleMovie);

            SaveMovieDTO result = movieService.saveMovie(adminMovieId);

            assertThat(result).isNotNull();
            assertThat(sampleMovie.getDeletedAt()).isNull(); // Verified restored
            verify(movieRepository, times(1)).save(sampleMovie);
        }

        @Test
        @DisplayName("Should throw BadRequestException if TMDB release date is blank")
        void shouldThrowExceptionForMissingReleaseDate() throws Exception {
            int adminMovieId = 123;
            MovieDb tmdbMovie = mock(MovieDb.class);
            when(tmdbMovie.getId()).thenReturn(adminMovieId);
            when(tmdbMovie.getReleaseDate()).thenReturn(""); // Blank date

            when(tmdbApi.getMovies()).thenReturn(tmdbMovies);
            when(tmdbMovies.getDetails(adminMovieId, "en-US")).thenReturn(tmdbMovie);

            assertThatThrownBy(() -> movieService.saveMovie(adminMovieId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("TMDB release date is missing.");
        }
        @Test
        @DisplayName("Should throw BadRequestException when TMDB release date format is unparseable")
        void shouldThrowExceptionForMalformedReleaseDateFormat() throws Exception {
            int adminMovieId = 123;
            MovieDb tmdbMovie = mock(MovieDb.class);
            when(tmdbMovie.getId()).thenReturn(adminMovieId);
            when(tmdbMovie.getReleaseDate()).thenReturn("01-01-2026"); // Invalid ISO string formatting

            when(tmdbApi.getMovies()).thenReturn(tmdbMovies);
            when(tmdbMovies.getDetails(adminMovieId, "en-US")).thenReturn(tmdbMovie);

            assertThatThrownBy(() -> movieService.saveMovie(adminMovieId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("TMDB release date is invalid.");
        }

        @Test
        @DisplayName("Should throw BadRequestException if TMDB release date is null entirely")
        void shouldThrowExceptionForNullReleaseDate() throws Exception {
            int adminMovieId = 123;
            MovieDb tmdbMovie = mock(MovieDb.class);
            when(tmdbMovie.getId()).thenReturn(adminMovieId);
            when(tmdbMovie.getReleaseDate()).thenReturn(null);

            when(tmdbApi.getMovies()).thenReturn(tmdbMovies);
            when(tmdbMovies.getDetails(adminMovieId, "en-US")).thenReturn(tmdbMovie);

            assertThatThrownBy(() -> movieService.saveMovie(adminMovieId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("TMDB release date is missing.");
        }
    }

    @Nested
    @DisplayName("Tests for getUserMovieList")
    class GetUserMovieListTests {

        @Test
        @DisplayName("Should fetch user movies dynamically filtering by specifications")
        void shouldFetchUserMoviesWithFilters() {
            Page<Movie> pageMock = new PageImpl<>(List.of(sampleMovie));
            when(movieRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageMock);

            Page<MovieDTO> result = movieService.getUserMovieList(
                    1, 10, "Inception", 8.0, 9.5, "12+", "2010-01-01", "2010-12-31", "ACTION"
            );

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().title()).isEqualTo("Inception");
        }

        @Test
        @DisplayName("Should catch unexpected exceptions and wrap them in a BadRequestException")
        void shouldThrowBadRequestOnFailure() {
            when(movieRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenThrow(new RuntimeException("Database error"));

            assertThatThrownBy(() -> movieService.getUserMovieList(1, 10, null, null, null, null, null, null, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Error while fetching movies.");
        }

        @Test
        @DisplayName("Should cover branch where title is null or blank")
        void shouldHandleNullOrBlankTitleFilter() {
            Page<Movie> pageMock = new PageImpl<>(List.of(sampleMovie));
            when(movieRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageMock);

            // Passing "" and null to satisfy short circuit combinations
            movieService.getUserMovieList(1, 10, "", null, null, null, null, null, null);
            movieService.getUserMovieList(1, 10, null, null, null, null, null, null, null);

            verify(movieRepository, times(2)).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        @DisplayName("Should cover branches for age rating filtering variants")
        void shouldHandleAgeRatingFilterVariants() {
            Page<Movie> pageMock = new PageImpl<>(List.of(sampleMovie));
            when(movieRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageMock);

            // 1. Valid string (triggers block execution)
            movieService.getUserMovieList(1, 10, null, null, null, "16+", null, null, null);
            // 2. Blank string (fails second part of short-circuit verification)
            movieService.getUserMovieList(1, 10, null, null, null, "   ", null, null, null);

            verify(movieRepository, times(2)).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        @DisplayName("Should cover branches for releaseFrom and releaseTo date filtering options")
        void shouldHandleReleaseDateRangeFilters() {
            Page<Movie> pageMock = new PageImpl<>(List.of(sampleMovie));
            when(movieRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageMock);

            // Covers parsing logic and block entrance
            movieService.getUserMovieList(1, 10, null, null, null, null, "2026-01-01", "2026-12-31", null);
            // Covers blank parameters logic
            movieService.getUserMovieList(1, 10, null, null, null, null, "", "   ", null);

            verify(movieRepository, times(2)).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        @DisplayName("Should cover branches for genre filtering execution paths")
        void shouldHandleGenreFilters() {
            Page<Movie> pageMock = new PageImpl<>(List.of(sampleMovie));
            when(movieRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageMock);

            // Trigger criteria building for Join
            movieService.getUserMovieList(1, 10, null, null, null, null, null, null, "ACTION");
            movieService.getUserMovieList(1, 10, null, null, null, null, null, null, "");

            verify(movieRepository, times(2)).findAll(any(Specification.class), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("Tests for getMovie")
    class GetMovieTests {

        @Test
        @DisplayName("Should return MovieDTO when an active movie is found by ID")
        void shouldReturnMovieWhenFound() {
            when(movieRepository.findById(100L)).thenReturn(Optional.of(sampleMovie));

            MovieDTO result = movieService.getMovie(100L);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(100L);
        }

        @Test
        @DisplayName("Should throw NotFoundException if movie is soft-deleted")
        void shouldThrowNotFoundIfSoftDeleted() {
            sampleMovie.setDeletedAt(LocalDateTime.now());
            when(movieRepository.findById(100L)).thenReturn(Optional.of(sampleMovie));

            assertThatThrownBy(() -> movieService.getMovie(100L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Movie not found.");
        }

        @Test
        @DisplayName("Should throw NotFoundException if movie is completely missing")
        void shouldThrowNotFoundIfMissing() {
            when(movieRepository.findById(100L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> movieService.getMovie(100L))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Tests for updateMovie")
    class UpdateMovieTests {

        @Test
        @DisplayName("Should update properties and save successfully")
        void shouldUpdateMovieSuccessfully() {
            // Arrange
            SaveMovieDTO updateDto = new SaveMovieDTO(
                    "Inception Remastered", LocalDateTime.now(), "New description", 9.0, 150, "16+", List.of(GenreType.ACTION)
            );
            when(movieRepository.findById(100L)).thenReturn(Optional.of(sampleMovie));
            when(genreRepository.findByTypeIn(any())).thenReturn(List.of(sampleGenre));
            when(movieRepository.save(any(Movie.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            MovieDTO result = movieService.updateMovie(100L, updateDto);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("Inception Remastered");
            assertThat(result.ageRating()).isEqualTo("16+");
            verify(movieRepository).save(sampleMovie);
        }

        @Test
        @DisplayName("Should throw NotFoundException if trying to update a soft-deleted movie")
        void shouldNotUpdateSoftDeletedMovie() {
            sampleMovie.setDeletedAt(LocalDateTime.now());
            SaveMovieDTO updateDto = new SaveMovieDTO("Title", LocalDateTime.now(), "Desc", 5.0, 120, "12+", List.of());
            when(movieRepository.findById(100L)).thenReturn(Optional.of(sampleMovie));

            assertThatThrownBy(() -> movieService.updateMovie(100L, updateDto))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Should successfully resolve and save dynamically found genres when updating database items")
        void shouldCreateGenresIfTheyDoNotYetExistInDatabase() {
            SaveMovieDTO updateDto = new SaveMovieDTO(
                    "Inception Remastered", LocalDateTime.now(), "New description", 9.0, 150, "16+",
                    List.of(GenreType.ACTION, GenreType.COMEDY)
            );

            when(movieRepository.findById(100L)).thenReturn(Optional.of(sampleMovie));
            // Simulate database only recognizing ACTION, making COMEDY dynamically created
            when(genreRepository.findByTypeIn(any())).thenReturn(List.of(sampleGenre));
            when(genreRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(movieRepository.save(any(Movie.class))).thenAnswer(invocation -> invocation.getArgument(0));

            movieService.updateMovie(100L, updateDto);

            verify(genreRepository, times(1)).saveAll(any());
            verify(movieRepository).save(sampleMovie);
        }

        @Test
        @DisplayName("Should safely assign empty collection structures when update list references are absent")
        void shouldReturnEmptyListWhenProvidedGenreTypesListIsEmptyOrNull() {
            SaveMovieDTO updateDtoNullGenres = new SaveMovieDTO(
                    "No Genres", LocalDateTime.now(), "Desc", 5.0, 120, "12+", null
            );
            when(movieRepository.findById(100L)).thenReturn(Optional.of(sampleMovie));
            when(movieRepository.save(any(Movie.class))).thenAnswer(invocation -> invocation.getArgument(0));

            MovieDTO result = movieService.updateMovie(100L, updateDtoNullGenres);

            assertThat(result.genres()).isEmpty();
            verify(genreRepository, never()).findByTypeIn(any());
        }
    }

    @Nested
    @DisplayName("Tests for deleteMovie")
    class DeleteMovieTests {

        @Test
        @DisplayName("Should set deletedAt timestamp and clear genres on deletion happy path")
        void shouldSoftDeleteMovie() {
            // Arrange
            when(movieRepository.findById(100L)).thenReturn(Optional.of(sampleMovie));

            // Act
            movieService.deleteMovie(100L);

            // Assert
            assertThat(sampleMovie.getDeletedAt()).isNotNull();
            assertThat(sampleMovie.getGenres()).isEmpty();
            verify(movieRepository, times(1)).save(sampleMovie);
        }

        @Test
        @DisplayName("Should do nothing if the movie is already soft-deleted")
        void shouldDoNothingIfAlreadyDeleted() {
            // Arrange
            sampleMovie.setDeletedAt(LocalDateTime.now());
            when(movieRepository.findById(100L)).thenReturn(Optional.of(sampleMovie));

            // Act
            movieService.deleteMovie(100L);

            // Assert
            verify(movieRepository, never()).save(any());
        }
    }
}