package com.awbd.cinema.services.MovieService;

import com.awbd.cinema.DTOs.MovieDTOs.AdminMovieDTO;
import com.awbd.cinema.DTOs.MovieDTOs.SaveMovieDTO;
import com.awbd.cinema.DTOs.MovieDTOs.MovieDTO;
import com.awbd.cinema.entities.Genre;
import com.awbd.cinema.entities.Movie;
import com.awbd.cinema.enums.GenreType;
import com.awbd.cinema.repositories.GenreRepository;
import com.awbd.cinema.exceptions.AlreadyExistsException;
import com.awbd.cinema.exceptions.BadRequestException;
import com.awbd.cinema.exceptions.NotFoundException;
import com.awbd.cinema.repositories.MovieRepository;
import com.awbd.cinema.utils.RestPage;
import info.movito.themoviedbapi.TmdbApi;
import info.movito.themoviedbapi.model.movies.MovieDb;
import info.movito.themoviedbapi.tools.TmdbException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

@Service
@RequiredArgsConstructor
@Slf4j
public class MovieServiceImpl implements MovieService {

    private final TmdbApi tmdbApi;
    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;

    @Cacheable(value = "admin_movies", key = "#page ?: 1")
    public RestPage<AdminMovieDTO> getAdminMovieList(Integer page) {
        try {
            int tmdbPage = (page == null || page < 1) ? 1 : page;
            var popularMovies = tmdbApi.getMovieLists().getPopular("en-US", tmdbPage, "GBR");
            List<AdminMovieDTO> movies = popularMovies.getResults().stream()
                    .map(AdminMovieDTO::from)
                    .toList();

            int tmdbPageSize = 20;
            Pageable pageable = PageRequest.of(tmdbPage - 1, tmdbPageSize);
            return new RestPage<>(new PageImpl<>(movies, pageable, popularMovies.getTotalResults()));
        } catch (TmdbException e) {
            log.error("Error while fetching movies: {}", e.getMessage());
            throw new BadRequestException("Error while fetching movies.");
        }
    }

    @Transactional
    @CacheEvict(value = "public_movie_lists", allEntries = true)
    public SaveMovieDTO saveMovie(int adminMovieId) {
        try {
            MovieDb tmdbMovie = tmdbApi.getMovies().getDetails(adminMovieId, "en-US");
            Long tmdbMovieId = (long) tmdbMovie.getId();

            Optional<Movie> existingMovieOpt = Optional.ofNullable(movieRepository.findByIdIncludingDeleted(tmdbMovieId));

            List<Genre> genres = resolveGenres(tmdbMovie.getGenres());

            if (existingMovieOpt.isPresent()) {
                Movie movie = existingMovieOpt.get();
                if (movie.getDeletedAt() == null) {
                    throw new AlreadyExistsException("Movie already exists.");
                } else {
                    movie.setDeletedAt(null);
                    movie.setGenres(genres);
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
                    .genres(genres)
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

    @Transactional(readOnly = true)
    @Cacheable(value = "public_movie_lists")
    public Page<MovieDTO> getUserMovieList(Integer page, Integer size, String title, Double minRating, Double maxRating, String ageRating, String releaseFrom, String releaseTo, String genre) {
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

                if (genre != null && !genre.isBlank()) {
                    GenreType genreType = GenreType.valueOf(genre.toUpperCase());
                    Join<Movie, Genre> genreJoin = root.join("genres", JoinType.INNER);
                    predicates.add(cb.equal(genreJoin.get("type"), genreType));
                }

                return cb.and(predicates.toArray(new Predicate[0]));
            };

            return movieRepository.findAll(spec, pageable).map(MovieDTO::from);
        } catch (Exception e) {
            log.error("Error while fetching movies: {}", e.getMessage());
            throw new BadRequestException("Error while fetching movies.");
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "single_movies", key = "#id")
    public MovieDTO getMovie(Long id) {
        return movieRepository.findById(id)
                .filter(m -> m.getDeletedAt() == null)
                .map(MovieDTO::from)
                .orElseThrow(() -> new NotFoundException("Movie not found."));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_movies", key = "#id"),
            @CacheEvict(value = "public_movie_lists", allEntries = true)
    })
    public MovieDTO updateMovie(Long id, SaveMovieDTO dto) {
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new NotFoundException("Movie not found."));
        if (movie.getDeletedAt() != null) throw new NotFoundException("Movie not found.");

        movie.setTitle(dto.title());
        movie.setReleaseDate(dto.releaseDate());
        movie.setDescription(dto.description());
        movie.setRating(dto.rating());
        movie.setDuration(dto.duration());
        movie.setAgeRating(dto.ageRating());
        movie.setGenres(resolveGenresByType(dto.genres()));

        Movie saved = movieRepository.save(movie);
        return MovieDTO.from(saved);
    }

    private List<Genre> resolveGenresByType(List<GenreType> types) {
        if (types == null || types.isEmpty()) return List.of();

        List<Genre> existing = genreRepository.findByTypeIn(types);
        Set<GenreType> existingTypes = existing.stream()
                .map(Genre::getType)
                .collect(Collectors.toSet());

        List<Genre> created = types.stream()
                .filter(type -> !existingTypes.contains(type))
                .map(type -> Genre.builder().type(type).build())
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        toCreate -> toCreate.isEmpty() ? List.of() : genreRepository.saveAll(toCreate)
                ));

        List<Genre> result = new ArrayList<>(existing);
        result.addAll(created);
        return result;
    }

    private List<Genre> resolveGenres(List<info.movito.themoviedbapi.model.core.Genre> tmdbGenres) {
        if (tmdbGenres == null || tmdbGenres.isEmpty()) return List.of();
        List<GenreType> types = tmdbGenres.stream()
                .map(tg -> GenreType.fromTmdbId(tg.getId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        return resolveGenresByType(types);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "single_movies", key = "#id"),
            @CacheEvict(value = "public_movie_lists", allEntries = true)
    })
    public void deleteMovie(Long id) {
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new NotFoundException("Movie not found."));
        if (movie.getDeletedAt() == null) {
            movie.setDeletedAt(LocalDateTime.now());
            movie.setGenres(new ArrayList<>());
            movieRepository.save(movie);
        }
    }
}
