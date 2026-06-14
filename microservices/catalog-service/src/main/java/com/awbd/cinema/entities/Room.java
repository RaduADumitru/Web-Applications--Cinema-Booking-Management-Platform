package com.awbd.cinema.entities;

import com.awbd.cinema.enums.RoomType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "rooms")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    @NotNull(message = "The room type is required.")
    private RoomType type;

    @Column(name = "name", nullable = false)
    @NotBlank(message = "The room name is required.")
    private String name;

    @Column(name = "floor", nullable = false)
    @NotNull(message = "The floor is required.")
    private Integer floor;

    @ManyToMany
    @JoinTable(
        name = "room_screen_sessions",
        joinColumns = @JoinColumn(name = "room_id"),
        inverseJoinColumns = @JoinColumn(name = "session_id"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "session_id"})
    )
    private List<ScreenSession> screenSessions;

    @ManyToMany
    @JoinTable(
        name = "room_seats",
        joinColumns = @JoinColumn(name = "room_id"),
        inverseJoinColumns = @JoinColumn(name = "seat_id"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "seat_id"})
    )
    private List<Seat> seats;
}
