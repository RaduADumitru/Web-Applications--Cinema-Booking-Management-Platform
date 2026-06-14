package com.awbd.cinema.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "points_spend")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PointsSpend {

    private static final int POINTS_PER_UNIT = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "points_spend_id")
    private Long id;

    @Column(name = "points_used", nullable = false)
    @NotNull(message = "Points used is required.")
    @Min(value = 1, message = "At least 1 point must be spent.")
    private Integer pointsUsed;

    @Column(name = "discount", nullable = false, precision = 10, scale = 2)
    @NotNull(message = "Discount is required.")
    private BigDecimal discount;

    public static BigDecimal calculateDiscount(int points) {
        return BigDecimal.valueOf(points)
                .divide(BigDecimal.valueOf(POINTS_PER_UNIT), 2, RoundingMode.FLOOR);
    }
}
