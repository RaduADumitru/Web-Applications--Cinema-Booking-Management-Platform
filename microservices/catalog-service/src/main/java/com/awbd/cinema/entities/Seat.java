package com.awbd.cinema.entities;

import com.awbd.cinema.enums.SeatZone;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "seats")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long id;

    @Column(name = "row", nullable = false)
    @NotNull(message = "The row is required.")
    @Positive(message = "The row must be a positive number.")
    private Integer row;

    @Column(name = "number", nullable = false)
    @NotNull(message = "The seat number is required.")
    @Positive(message = "The seat number must be a positive number.")
    private Integer number;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone", nullable = false)
    @NotNull(message = "The zone is required.")
    private SeatZone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private SeatCategory category;

    @ManyToMany(mappedBy = "seats")
    private List<Room> rooms;
}
