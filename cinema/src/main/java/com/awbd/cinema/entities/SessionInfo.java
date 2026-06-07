package com.awbd.cinema.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import com.awbd.cinema.enums.Format;

import java.util.List;

@Entity
@Table(name = "session_info")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SessionInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_info_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    @NotNull(message = "The format is required.")
    private Format format;

    @Column(name = "points", nullable = false)
    @NotNull(message = "The points value is required.")
    @PositiveOrZero(message = "Points must be a non-negative number.")
    private Integer points;

    @OneToMany(mappedBy = "sessionInfo")
    private List<ScreenSession> screenSessions;
}
