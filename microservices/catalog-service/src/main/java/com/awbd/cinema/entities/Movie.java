package com.awbd.cinema.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "movies")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Movie {
    @Id
    @Column(name = "movie_id")
    private Long id; //uses tmdb's id, don't autogenerate

    @Column(name = "title", unique = true, nullable = false)
    @NotBlank(message = "The movie title field is required.")
    private String title;

    @Column(name = "duration_min", nullable = false)
    @NotNull(message = "The duration is required.")
    @PositiveOrZero(message = "The duration must be a positive number.")
    private Integer duration;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
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

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScreenSession> screenSessions;

    @ManyToMany
    @JoinTable(
        name = "movie_genres",
        joinColumns = @JoinColumn(name = "movie_id"),
        inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private List<Genre> genres;

    @Column(name = "image_path")
    private String imagePath;
}
