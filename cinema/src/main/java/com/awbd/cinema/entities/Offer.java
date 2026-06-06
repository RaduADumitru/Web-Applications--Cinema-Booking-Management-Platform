package com.awbd.cinema.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.DayOfWeek;
import java.util.List;

@Entity
@Table(name = "offers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "offer_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "day", nullable = false, unique = true)
    @NotNull(message = "Day is required.")
    private DayOfWeek day;

    @Column(name = "percent", nullable = false)
    @NotNull(message = "Percent is required.")
    @Min(value = 1, message = "Percent must be at least 1.")
    @Max(value = 100, message = "Percent must be at most 100.")
    private Integer percent;

    @OneToMany(mappedBy = "offer")
    private List<Order> orders;
}
