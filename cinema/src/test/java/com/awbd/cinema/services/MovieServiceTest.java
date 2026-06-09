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
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

        // ==========================================
        // 1. PAGINATION MATH BRANCH TESTS
        // ==========================================
        @Test
        @DisplayName("Should resolve correct fallback page indices when pagination parameters are null or negative")
        void shouldHandleInvalidPaginationParameters() {
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            Page<Movie> pageMock = new PageImpl<>(List.of(sampleMovie));
            when(movieRepository.findAll(any(Specification.class), pageableCaptor.capture())).thenReturn(pageMock);

            // Mix 1: Null bounds (Should fallback to p=0, s=10)
            movieService.getUserMovieList(null, null, null, null, null, null, null, null, null);

            // Mix 2: Below zero bounds (Should fallback to p=0, s=10)
            movieService.getUserMovieList(0, -5, null, null, null, null, null, null, null);

            // Mix 3: Valid bounds (Should offset p = page - 1, s=size)
            movieService.getUserMovieList(3, 15, null, null, null, null, null, null, null);

            List<Pageable> captured = pageableCaptor.getAllValues();

            // Assert Mix 1
            assertThat(captured.get(0).getPageNumber()).isEqualTo(0);
            assertThat(captured.get(0).getPageSize()).isEqualTo(10);

            // Assert Mix 2
            assertThat(captured.get(1).getPageNumber()).isEqualTo(0);
            assertThat(captured.get(1).getPageSize()).isEqualTo(10);

            // Assert Mix 3
            assertThat(captured.get(2).getPageNumber()).isEqualTo(2); // 3 - 1
            assertThat(captured.get(2).getPageSize()).isEqualTo(15);
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

        // ==========================================
        // 2. SPECIFICATION LAMBDA BRANCH TESTS
        // ==========================================
        @Nested
        @DisplayName("Specification Inner Lambda Tests")
        class SpecificationLambdaTests {

            @Mock private Root<Movie> root;
            @Mock private CriteriaQuery<?> query;
            @Mock private CriteriaBuilder cb;
            @Mock private Join<Movie, Genre> genreJoin;
            @Mock private Path<Object> pathMock;
            @Mock private Expression<String> stringExpressionMock;

            @SuppressWarnings("unchecked")
            private Specification<Movie> captureSpec(String title, Double minRating, Double maxRating,
                                                     String ageRating, String releaseFrom, String releaseTo, String genre) {
                ArgumentCaptor<Specification<Movie>> specCaptor = ArgumentCaptor.forClass(Specification.class);
                Page<Movie> pageMock = new PageImpl<>(List.of(sampleMovie));
                when(movieRepository.findAll(specCaptor.capture(), any(Pageable.class))).thenReturn(pageMock);

                movieService.getUserMovieList(1, 10, title, minRating, maxRating, ageRating, releaseFrom, releaseTo, genre);
                return specCaptor.getValue();
            }

            @Test
            @DisplayName("Should evaluate true for all conditional branches when every filter parameter is valid")
            void toPredicate_WithAllFiltersValid_ExecutesAllTrueBranches() {
                Specification<Movie> spec = captureSpec(
                        "Inception", 8.0, 9.5, "12+", "2010-01-01", "2010-12-31", "ACTION"
                );

                // Stubbing title lower operations
                when(root.get("title")).thenReturn(pathMock);
                when(cb.lower(any())).thenReturn(stringExpressionMock);

                // Stubbing primitive fields
                when(root.get("rating")).thenReturn(pathMock);
                when(root.get("ageRating")).thenReturn(pathMock);
                when(root.get("releaseDate")).thenReturn(pathMock);

                // Stubbing join execution
                doReturn(genreJoin).when(root).join(eq("genres"), eq(JoinType.INNER));
                when(genreJoin.get("type")).thenReturn(pathMock);

                // Act
                spec.toPredicate(root, query, cb);

                // Assert conversions and entries executed perfectly
                verify(cb).like(any(), eq("%inception%"));
                verify(cb).greaterThanOrEqualTo(any(), eq(8.0));
                verify(cb).lessThanOrEqualTo(any(), eq(9.5));
                verify(cb).equal(any(), eq("12+"));
                verify(cb).equal(any(), eq(GenreType.ACTION));
                verify(cb).and(any(Predicate[].class));
            }

            @Test
            @DisplayName("Should evaluate false for all validation paths when parameters are null, empty, or blank whitespace strings")
            void toPredicate_WithBlankAndNullFilters_ExecutesAllFalseBranches() {
                // Mix matching null values alongside blank strings to evaluate short circuits
                Specification<Movie> spec = captureSpec(
                        "   ", null, null, "", "  ", null, ""
                );

                // Act
                spec.toPredicate(root, query, cb);

                // Verify that zero criteria methods or entity reflections were triggered
                verify(root, never()).get(anyString());
                verify(root, never()).join(anyString(), any(JoinType.class));
                verify(cb, never()).like(any(), anyString());
                verify(cb, never()).equal(any(), any());
                verify(cb).and(any(Predicate[].class));
            }
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