package com.awbd.cinema.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "movies")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Movie {
    @Id
    @Column(name = "movie_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", unique = true, nullable = false)
    @NotBlank(message = "The movie title field is required.")
    private String title;

    @Column(name = "duration_min", nullable = false)
    @NotBlank(message = "The duration is required.")
    private Integer duration;

    @Column(name = "description", nullable = false)
    @NotNull(message = "The description field is required.")
    private String description;

    @Column(name = "rating", nullable = false)
    @NotNull(message = "The rating is required.")
    private Double rating;

    @Column(name = "release_date", nullable = false)
    @NotNull(message = "The release date field is required.")
    private LocalDateTime releaseDate;

    @Column(name="age_rating", nullable = false)
    @NotNull(message = "The age rating is required.")
    private String ageRating;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
