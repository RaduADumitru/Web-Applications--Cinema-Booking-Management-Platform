package com.awbd.cinema.DTOs.MovieDTOs;

import com.awbd.cinema.entities.Movie;
import com.awbd.cinema.enums.GenreType;

import java.time.LocalDateTime;
import java.util.List;

public record MovieDTO(
        Long id,
        String title,
        LocalDateTime releaseDate,
        String description,
        Double rating,
        Integer duration,
        String ageRating,
        List<GenreType> genres,
        String imagePath
) {
    public static MovieDTO from(Movie m) {
        List<GenreType> genres = m.getGenres() == null ? List.of() :
                m.getGenres().stream().map(g -> g.getType()).toList();
        return new MovieDTO(
                m.getId(),
                m.getTitle(),
                m.getReleaseDate(),
                m.getDescription(),
                m.getRating(),
                m.getDuration(),
                m.getAgeRating(),
                genres,
                m.getImagePath()
        );
    }
}
