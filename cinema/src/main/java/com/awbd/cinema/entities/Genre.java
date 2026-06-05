package com.awbd.cinema.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.awbd.cinema.enums.GenreType;

import java.util.List;

@Entity
@Table(name = "genres")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Genre {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "genre_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, unique = true)
    @NotNull(message = "The genre type is required.")
    private GenreType type;

    @ManyToMany(mappedBy = "genres")
    private List<Movie> movies;
}
