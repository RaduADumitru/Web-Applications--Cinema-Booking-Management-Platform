package com.awbd.cinema.entities;

import com.awbd.cinema.enums.TicketType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
    name = "tickets",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ticket_seat_room_session",
        columnNames = {"seat_id", "room_id", "session_id"}
    )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Long id;

    @Column(name = "is_available", nullable = false)
    private boolean isAvailable;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private TicketType type;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "session_id", nullable = false)
    private Long screenSessionId;

    // Denormalized snapshot of catalog data, fetched once at creation via catalog /internal/ticket-setup
    @Column(name = "seat_row")
    private Integer seatRow;

    @Column(name = "seat_number")
    private Integer seatNumber;

    @Column(name = "seat_zone")
    private String seatZone;

    @Column(name = "extra_fee", precision = 10, scale = 2)
    private BigDecimal extraFee;

    @Column(name = "extra_points")
    private Integer extraPoints;

    @Column(name = "movie_title")
    private String movieTitle;

    @Column(name = "session_date")
    private LocalDate sessionDate;

    @Column(name = "session_start_time")
    private LocalTime sessionStartTime;

    @Column(name = "session_points")
    private Integer sessionPoints;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_info_id")
    private TicketInfo ticketInfo;
}
