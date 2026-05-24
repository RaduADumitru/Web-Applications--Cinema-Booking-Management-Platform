package com.awbd.cinema.DTOs.MovieDTOs;

import com.awbd.cinema.entities.Movie;

import java.time.LocalDateTime;

public record SaveMovieDTO(
        String title,
        LocalDateTime releaseDate,
        String description,
        Double rating,
        Integer duration,
        String ageRating
) {
    public static SaveMovieDTO from(Movie movie) {
        return new SaveMovieDTO(
                movie.getTitle(),
                movie.getReleaseDate(),
                movie.getDescription(),
                movie.getRating(),
                movie.getDuration(),
                movie.getAgeRating()
        );
    }
}
