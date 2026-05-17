package com.awbd.cinema.DTOs.MovieDTOs;

import com.awbd.cinema.entities.Movie;

import java.time.LocalDateTime;

public record MovieDTO(
        Long id,
        String title,
        LocalDateTime releaseDate,
        String description,
        Double rating,
        Integer duration,
        String ageRating
) {
    public static MovieDTO from(Movie m) {
        return new MovieDTO(
                m.getId(),
                m.getTitle(),
                m.getReleaseDate(),
                m.getDescription(),
                m.getRating(),
                m.getDuration(),
                m.getAgeRating()
        );
    }
}
