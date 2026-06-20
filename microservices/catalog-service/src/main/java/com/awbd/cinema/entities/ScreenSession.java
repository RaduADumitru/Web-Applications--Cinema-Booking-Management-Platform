package com.awbd.cinema.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "screen_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScreenSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long id;

    @Column(name = "session_date", nullable = false)
    @NotNull(message = "The date is required.")
    @jakarta.validation.constraints.FutureOrPresent(message = "The date must be today or in the future.")
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    @NotNull(message = "The start time is required.")
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    @NotNull(message = "The end time is required.")
    private LocalTime endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    @NotNull(message = "The movie is required.")
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_info_id")
    private SessionInfo sessionInfo;

    @ManyToMany(mappedBy = "screenSessions")
    private List<Room> rooms;
}
