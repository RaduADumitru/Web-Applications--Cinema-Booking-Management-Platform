package com.awbd.cinema.entities;

import com.awbd.cinema.enums.TicketType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "ticket_info")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TicketInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_info_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, unique = true)
    @NotNull(message = "The ticket type is required.")
    private TicketType type;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    @NotNull(message = "The price is required.")
    @PositiveOrZero(message = "Price must be a non-negative number.")
    private BigDecimal price;

    @OneToMany(mappedBy = "ticketInfo")
    private List<Ticket> tickets;
}
