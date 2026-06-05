package com.awbd.cinema.entities;

import com.awbd.cinema.enums.SeatCategoryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "seat_categories")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SeatCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, unique = true)
    @NotNull(message = "The category type is required.")
    private SeatCategoryType type;

    @Column(name = "extra_fee", nullable = false, precision = 10, scale = 2)
    @NotNull(message = "The extra fee is required.")
    @PositiveOrZero(message = "The extra fee must be a non-negative number.")
    private BigDecimal extraFee;

    @Column(name = "extra_points", nullable = false)
    @NotNull(message = "The extra points are required.")
    @PositiveOrZero(message = "The extra points must be a non-negative number.")
    private Integer extraPoints;

    @OneToMany(mappedBy = "category")
    private List<Seat> seats;
}
