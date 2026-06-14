package com.awbd.cinema.DTOs.MovieDTOs;

import com.awbd.cinema.entities.Movie;
import com.awbd.cinema.enums.GenreType;

import java.time.LocalDateTime;
import java.util.List;

public record SaveMovieDTO(
        String title,
        LocalDateTime releaseDate,
        String description,
        Double rating,
        Integer duration,
        String ageRating,
        List<GenreType> genres
) {
    public static SaveMovieDTO from(Movie movie) {
        List<GenreType> genres = movie.getGenres() == null ? List.of() :
                movie.getGenres().stream().map(g -> g.getType()).toList();
        return new SaveMovieDTO(
                movie.getTitle(),
                movie.getReleaseDate(),
                movie.getDescription(),
                movie.getRating(),
                movie.getDuration(),
                movie.getAgeRating(),
                genres
        );
    }
}
