package com.awbd.cinema.DTOs.MovieDTOs;

import info.movito.themoviedbapi.model.core.Movie;

import java.time.LocalDateTime;
import java.util.List;

public record AdminMovieDTO(
        int id,
        String title,
        String imageUrl,
        LocalDateTime releaseDate,
        List<Integer> genreIds,
        Double rating,
        String description
) {
    public static AdminMovieDTO from(Movie movie) {
        return new AdminMovieDTO(
                movie.getId(),
                movie.getTitle(),
                "https://image.tmdb.org/t/p/w600_and_h900_face" + movie.getPosterPath(),
                movie.getReleaseDate() != null ?
                        java.time.LocalDate.parse(movie.getReleaseDate()).atStartOfDay() : null,
                movie.getGenreIds(),
                movie.getVoteAverage(),
                movie.getOverview()
        );
    }
}
