package com.awbd.cinema.services.MovieService;

import com.awbd.cinema.DTOs.MovieDTOs.AdminMovieDTO;
import com.awbd.cinema.DTOs.MovieDTOs.SaveMovieDTO;
import com.awbd.cinema.DTOs.MovieDTOs.MovieDTO;
import com.awbd.cinema.entities.Movie;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.MovieRepository;
import info.movito.themoviedbapi.TmdbApi;
import info.movito.themoviedbapi.model.movies.MovieDb;
import info.movito.themoviedbapi.tools.TmdbException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

@Service
@RequiredArgsConstructor
@Slf4j
public class MovieServiceImpl implements MovieService {

    private final TmdbApi tmdbApi;
    private final MovieRepository movieRepository;

    public Page<AdminMovieDTO> getAdminMovieList(Integer page){
        try {
            int tmdbPage = (page == null || page < 1) ? 1 : page;
            var popularMovies = tmdbApi.getMovieLists().getPopular("en-US", tmdbPage, "GBR");
            List<AdminMovieDTO> movies = popularMovies.getResults().stream()
                    .map(AdminMovieDTO::from)
                    .toList();

            int tmdbPageSize = 20;
            Pageable pageable = PageRequest.of(tmdbPage - 1, tmdbPageSize);
            return new PageImpl<>(movies, pageable, popularMovies.getTotalResults());
        }
        catch (TmdbException e){
            log.error("Error while fetching movies: {}", e.getMessage());
            throw new BadRequestException("Error while fetching movies.");
        }
    }

    @Transactional
    public SaveMovieDTO saveMovie(int adminMovieId) {
        try {
            MovieDb tmdbMovie = tmdbApi.getMovies().getDetails(adminMovieId, "en-US");
            Long tmdbMovieId = (long) tmdbMovie.getId();

            Optional<Movie> existingMovieOpt = movieRepository.findById(tmdbMovieId);

            if (existingMovieOpt.isPresent()) {
                Movie movie = existingMovieOpt.get();
                if (movie.getDeletedAt() == null) {
                    throw new AlreadyExistsException("Movie already exists.");
                } else {
                    movie.setDeletedAt(null);
                    Movie savedMovie = movieRepository.save(movie);
                    return SaveMovieDTO.from(savedMovie);
                }
            }

            Movie m = Movie.builder()
                    .id(tmdbMovieId)
                    .title(tmdbMovie.getTitle())
                    .releaseDate(parseTmdbReleaseDate(tmdbMovie.getReleaseDate()))
                    .description(tmdbMovie.getOverview())
                    .rating(tmdbMovie.getVoteAverage())
                    .duration(tmdbMovie.getRuntime())
                    .ageRating(tmdbMovie.getAdult() ? "18+" : "12+")
                    .build();

            movieRepository.save(m);
            return SaveMovieDTO.from(m);

        } catch (TmdbException e) {
            log.error("Error while fetching movies: {}", e.getMessage());
            throw new BadRequestException("Error while fetching movies.");
        }
    }

    private LocalDateTime parseTmdbReleaseDate(String releaseDate) {
        if (releaseDate == null || releaseDate.isBlank()) {
            throw new BadRequestException("TMDB release date is missing.");
        }

        try {
            return LocalDate.parse(releaseDate.trim()).atStartOfDay();
        } catch (DateTimeParseException e) {
            log.warn("Invalid TMDB release date '{}': {}", releaseDate, e.getMessage());
            throw new BadRequestException("TMDB release date is invalid.");
        }
    }

    public Page<MovieDTO> getUserMovieList(Integer page, Integer size, String title, Double minRating, Double maxRating, String ageRating, String releaseFrom, String releaseTo) {
        try {
            int p = (page == null || page < 1) ? 0 : page - 1;
            int s = (size == null || size < 1) ? 10 : size;
            Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "releaseDate"));

            Specification<Movie> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();

                if (title != null && !title.isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase() + "%"));
                }

                if (minRating != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), minRating));
                }

                if (maxRating != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("rating"), maxRating));
                }

                if (ageRating != null && !ageRating.isBlank()) {
                    predicates.add(cb.equal(root.get("ageRating"), ageRating));
                }

                if (releaseFrom != null && !releaseFrom.isBlank()) {
                    LocalDate from = LocalDate.parse(releaseFrom);
                    predicates.add(cb.greaterThanOrEqualTo(root.get("releaseDate"), from.atStartOfDay()));
                }

                if (releaseTo != null && !releaseTo.isBlank()) {
                    LocalDate to = LocalDate.parse(releaseTo);
                    predicates.add(cb.lessThanOrEqualTo(root.get("releaseDate"), LocalDateTime.of(to, LocalTime.MAX)));
                }

                return cb.and(predicates.toArray(new Predicate[0]));
            };

            return movieRepository.findAll(spec, pageable).map(MovieDTO::from);
        } catch (Exception e) {
            log.error("Error while fetching movies: {}", e.getMessage());
            throw new BadRequestException("Error while fetching movies.");
        }
    }

    public MovieDTO getMovie(Long id) {
        return movieRepository.findById(id)
                .filter(m -> m.getDeletedAt() == null)
                .map(MovieDTO::from)
                .orElseThrow(() -> new NotFoundException("Movie not found."));
    }

    @Transactional
    public MovieDTO updateMovie(Long id, SaveMovieDTO dto) {
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new NotFoundException("Movie not found."));
        if (movie.getDeletedAt() != null) throw new NotFoundException("Movie not found.");

        movie.setTitle(dto.title());
        movie.setReleaseDate(dto.releaseDate());
        movie.setDescription(dto.description());
        movie.setRating(dto.rating());
        movie.setDuration(dto.duration());
        movie.setAgeRating(dto.ageRating());

        Movie saved = movieRepository.save(movie);
        return MovieDTO.from(saved);
    }

    @Transactional
    public void deleteMovie(Long id) {
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new NotFoundException("Movie not found."));
        if (movie.getDeletedAt() == null) {
            movie.setDeletedAt(LocalDateTime.now());
            movieRepository.save(movie);
        }
    }
}
