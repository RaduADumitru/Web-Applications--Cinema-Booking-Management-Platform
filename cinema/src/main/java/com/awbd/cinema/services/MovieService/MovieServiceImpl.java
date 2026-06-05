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
import info.movito.themoviedbapi.TmdbApi;
import info.movito.themoviedbapi.model.movies.MovieDb;
import info.movito.themoviedbapi.tools.TmdbException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String MOVIE_CACHE_PREFIX = "movie:";
    private static final Duration MOVIE_CACHE_TTL = Duration.ofHours(1);

    public Page<AdminMovieDTO> getAdminMovieList(Integer page) {
        try {
            int tmdbPage = (page == null || page < 1) ? 1 : page;
            var popularMovies = tmdbApi.getMovieLists().getPopular("en-US", tmdbPage, "GBR");
            List<AdminMovieDTO> movies = popularMovies.getResults().stream()
                    .map(AdminMovieDTO::from)
                    .toList();

            int tmdbPageSize = 20;
            Pageable pageable = PageRequest.of(tmdbPage - 1, tmdbPageSize);
            return new PageImpl<>(movies, pageable, popularMovies.getTotalResults());
        } catch (TmdbException e) {
            log.error("Error while fetching movies: {}", e.getMessage());
            throw new BadRequestException("Error while fetching movies.");
        }
    }

    @Transactional
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
                    redisTemplate.delete(MOVIE_CACHE_PREFIX + tmdbMovieId);
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
    public MovieDTO getMovie(Long id) {
        String key = MOVIE_CACHE_PREFIX + id;

        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof MovieDTO dto) {
            log.debug("Cache hit for movie {}", id);
            return dto;
        }

        MovieDTO dto = movieRepository.findById(id)
                .filter(m -> m.getDeletedAt() == null)
                .map(MovieDTO::from)
                .orElseThrow(() -> new NotFoundException("Movie not found."));

        redisTemplate.opsForValue().set(key, dto, MOVIE_CACHE_TTL);
        return dto;
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
        movie.setGenres(resolveGenresByType(dto.genres()));

        Movie saved = movieRepository.save(movie);
        redisTemplate.delete(MOVIE_CACHE_PREFIX + id);
        return MovieDTO.from(saved);
    }

    private List<Genre> resolveGenresByType(List<GenreType> types) {
        if (types == null || types.isEmpty()) return List.of();
        return types.stream()
                .map(type -> genreRepository.findByType(type)
                        .orElseGet(() -> genreRepository.save(Genre.builder().type(type).build())))
                .toList();
    }

    private List<Genre> resolveGenres(List<info.movito.themoviedbapi.model.core.Genre> tmdbGenres) {
        if (tmdbGenres == null || tmdbGenres.isEmpty()) return List.of();
        return tmdbGenres.stream()
                .map(tg -> mapTmdbGenreId(tg.getId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(type -> genreRepository.findByType(type)
                        .orElseGet(() -> genreRepository.save(Genre.builder().type(type).build())))
                .toList();
    }

    private Optional<GenreType> mapTmdbGenreId(int tmdbId) {
        return switch (tmdbId) {
            case 28    -> Optional.of(GenreType.ACTION);
            case 12    -> Optional.of(GenreType.ADVENTURE);
            case 16    -> Optional.of(GenreType.ANIMATION);
            case 35    -> Optional.of(GenreType.COMEDY);
            case 80    -> Optional.of(GenreType.CRIME);
            case 99    -> Optional.of(GenreType.DOCUMENTARY);
            case 18    -> Optional.of(GenreType.DRAMA);
            case 10751 -> Optional.of(GenreType.FAMILY);
            case 14    -> Optional.of(GenreType.FANTASY);
            case 36    -> Optional.of(GenreType.HISTORY);
            case 27    -> Optional.of(GenreType.HORROR);
            case 10402 -> Optional.of(GenreType.MUSIC);
            case 9648  -> Optional.of(GenreType.MYSTERY);
            case 10749 -> Optional.of(GenreType.ROMANCE);
            case 878   -> Optional.of(GenreType.SCI_FI);
            case 53    -> Optional.of(GenreType.THRILLER);
            case 10770 -> Optional.of(GenreType.TV_MOVIE);
            case 10752 -> Optional.of(GenreType.WAR);
            case 37    -> Optional.of(GenreType.WESTERN);
            default    -> Optional.empty();
        };
    }

    @Transactional
    public void deleteMovie(Long id) {
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new NotFoundException("Movie not found."));
        if (movie.getDeletedAt() == null) {
            movie.setDeletedAt(LocalDateTime.now());
            movie.setGenres(List.of());
            movieRepository.save(movie);
        }
        redisTemplate.delete(MOVIE_CACHE_PREFIX + id);
    }
}
